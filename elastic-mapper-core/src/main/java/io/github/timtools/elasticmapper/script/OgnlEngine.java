package io.github.timtools.elasticmapper.script;

import ognl.Ognl;
import ognl.OgnlException;

import java.util.Map;

/**
 * Thin static utility around OGNL expression parsing and evaluation.
 *
 * <p>Parsed expression trees ({@code Object} returned by
 * {@link Ognl#parseExpression(String)}) are immutable and thread-safe
 * — store them for repeated evaluation without re-parsing.
 */
public final class OgnlEngine {

    private OgnlEngine() {}

    /**
     * Parses an expression into an OGNL AST node.
     * The returned tree can be cached and re-evaluated against different contexts.
     */
    public static Object parse(String expression) throws OgnlException {
        return Ognl.parseExpression(expression);
    }

    /**
     * Evaluates a pre-parsed expression tree against a parameter map.
     *
     * @param tree  pre-parsed OGNL expression (from {@link #parse(String)})
     * @param root  the context map (parameters accessible by name)
     * @return the evaluation result
     */
    public static Object evaluate(Object tree, Map<String, Object> root) throws OgnlException {
        return Ognl.getValue(tree, root);
    }

    /**
     * Parses and evaluates in one shot. Use for one-off expressions;
     * for repeated evaluation prefer {@link #parse(String)} + {@link #evaluate(Object, Map)}.
     */
    public static Object evaluate(String expression, Map<String, Object> root) throws OgnlException {
        return Ognl.getValue(expression, root);
    }
}
