package io.elasticmapper.script;

import io.elasticmapper.parser.node.*;
import io.elasticmapper.parser.DynamicContext;
import io.elasticmapper.parser.StatementCompileException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A compiled dynamic statement: a StatementNode tree plus cached OGNL
 * expression ASTs for all test attributes. Created at startup, rendered
 * at runtime.
 */
public class DynamicStatement {

    private final String id;
    private final StatementNode root;
    /** expression text → pre-parsed OGNL tree (immutable, thread-safe) */
    private final Map<String, Object> compiledTests;

    private DynamicStatement(String id, StatementNode root, Map<String, Object> compiledTests) {
        this.id = id;
        this.root = root;
        this.compiledTests = Collections.unmodifiableMap(compiledTests);
    }

    public String getId() { return id; }
    public StatementNode getRoot() { return root; }

    /**
     * Renders the statement with the given parameter bindings.
     *
     * @param params named parameter values
     * @return the complete JSON string for the ES request
     */
    public String render(Map<String, Object> params) {
        OgnlDynamicContext ctx = new OgnlDynamicContext(params, compiledTests);
        StringBuilder out = new StringBuilder();
        root.render(ctx, out);
        return out.toString();
    }

    /**
     * Compiles a statement node tree at startup, pre-compiling all OGNL expressions.
     */
    public static DynamicStatement compile(String id, StatementNode root) {
        Map<String, Object> compiled = new LinkedHashMap<>();
        collectExpressions(root, compiled);
        return new DynamicStatement(id, root, compiled);
    }

    /**
     * Recursively collects and pre-compiles all test expressions from the node tree.
     */
    private static void collectExpressions(StatementNode node, Map<String, Object> compiled) {
        if (node == null) return;

        if (node instanceof IfNode) {
            String expr = ((IfNode) node).getTestExpression();
            compileIfAbsent(expr, compiled);
        }
        if (node instanceof WhenNode) {
            String expr = ((WhenNode) node).getTestExpression();
            compileIfAbsent(expr, compiled);
        }

        for (StatementNode child : node.getChildren()) {
            collectExpressions(child, compiled);
        }
    }

    private static void compileIfAbsent(String expr, Map<String, Object> compiled) {
        if (expr != null && !compiled.containsKey(expr)) {
            try {
                compiled.put(expr, OgnlEngine.parse(expr));
            } catch (Exception e) {
                throw new StatementCompileException(
                        "Failed to compile test expression: \"" + expr + "\" — " + e.getMessage(), e);
            }
        }
    }

    /**
     * OGNL-backed DynamicContext that uses pre-parsed expression trees
     * for test evaluation, avoiding re-parsing at runtime.
     */
    static class OgnlDynamicContext extends DynamicContext {

        private final Map<String, Object> compiledTests;

        OgnlDynamicContext(Map<String, Object> params, Map<String, Object> compiledTests) {
            super(params);
            this.compiledTests = compiledTests;
        }

        @Override
        public boolean evaluateTest(String expression) {
            Object tree = compiledTests.get(expression);
            if (tree != null) {
                try {
                    Object result = OgnlEngine.evaluate(tree, mergeParams());
                    if (result instanceof Boolean) return (Boolean) result;
                    return result != null;
                } catch (Exception e) {
                    throw new io.elasticmapper.executor.ElasticMapperException(
                            "EM-EXPR-001",
                            "Expression evaluation failed: \"" + expression + "\" — " + e.getMessage(), e);
                }
            }
            // Not pre-compiled — fall back to one-shot OGNL evaluation
            return super.evaluateTest(expression);
        }
    }
}
