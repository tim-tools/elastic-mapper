package io.github.timtools.elasticmapper.parser;

import io.github.timtools.elasticmapper.binding.MapperProxy;

import ognl.Ognl;
import ognl.OgnlException;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Runtime context for dynamic statement execution.
 *
 * <p>Holds parameter bindings and a variable stack (for foreach iteration).
 * Expression evaluation in {@link #evaluateTest(String)} is powered by
 * <a href="https://commons.apache.org/proper/commons-ognl/">OGNL</a> —
 * the same engine MyBatis uses for {@code <if test="...">}.
 *
 * <h3>Supported OGNL expressions</h3>
 * <pre>{@code
 * name != null                      // null check
 * name != null && name != ''        // chained conditions
 * age > 0                           // numeric comparison
 * status == 'active'                // string equality
 * !name.isEmpty()                   // method call
 * list != null && list.size() > 0   // collection check
 * }</pre>
 */
public class DynamicContext {

    private final Map<String, Object> params;
    private final Stack<Map<String, Object>> variableStack = new Stack<>();

    public DynamicContext(Map<String, Object> params) {
        this.params = new HashMap<>(params);
    }

    /**
     * Looks up a variable: checks the variable stack first, then named params.
     */
    public Object getParam(String name) {
        for (int i = variableStack.size() - 1; i >= 0; i--) {
            Map<String, Object> frame = variableStack.get(i);
            if (frame.containsKey(name)) {
                return frame.get(name);
            }
        }
        return params.get(name);
    }

    /**
     * Pushes a new variable scope frame (e.g. on foreach entry).
     * Variables pushed after this call are isolated in the new frame
     * and automatically removed when {@link #popFrame()} is called.
     */
    public void pushFrame() {
        variableStack.push(new HashMap<>());
    }

    /**
     * Pops the top variable scope frame, discarding all variables
     * that were pushed since the matching {@link #pushFrame()}.
     */
    public void popFrame() {
        if (!variableStack.isEmpty()) {
            variableStack.pop();
        }
    }

    /**
     * Pushes a foreach iteration variable into the current frame.
     * Creates a frame if the stack is empty (backward-compatible with
     * code that doesn't use explicit pushFrame/popFrame).
     */
    public void pushVariable(String name, Object value) {
        if (variableStack.isEmpty()) {
            variableStack.push(new HashMap<>());
        }
        variableStack.peek().put(name, value);
    }

    /**
     * Pops the named variable from the current frame only.
     */
    public void popVariable(String name) {
        if (!variableStack.isEmpty()) {
            variableStack.peek().remove(name);
        }
    }

    /**
     * Returns a merged view of all params and foreach variables.
     * Used as the OGNL root context for expression evaluation.
     */
    public Map<String, Object> mergeParams() {
        Map<String, Object> merged = new HashMap<>(params);
        for (Map<String, Object> frame : variableStack) {
            merged.putAll(frame);
        }
        return merged;
    }

    /**
     * Evaluates a test expression (e.g., from {@code <if test="...">})
     * using OGNL.
     *
     * <p>The merged parameter map (params + foreach variables) serves
     * as the OGNL root, so expressions reference parameters directly
     * by name: {@code name != null}, {@code age > 18}, etc.
     *
     * <p>If OGNL evaluation fails (e.g., malformed expression), falls
     * back to a simple truthiness check on the raw expression as a
     * parameter name.
     */
    public boolean evaluateTest(String expression) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }

        try {
            Object result = Ognl.getValue(expression, mergeParams());
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            // Non-boolean result → truthiness
            return result != null;
        } catch (OgnlException e) {
            // OGNL parse/eval failure — try simple truthiness fallback
            Object value = getParam(expression);
            if (value instanceof Boolean) return (Boolean) value;
            if (value instanceof String) return !((String) value).isEmpty();
            if (value instanceof Number) return ((Number) value).doubleValue() != 0;
            return value != null;
        }
    }

    /**
     * Replaces placeholders ({@code ${}} and {@code #{}}) in text.
     * Delegates to the same logic used by annotation queries.
     */
    public String replacePlaceholders(String text) {
        Map<String, Object> merged = new HashMap<>(params);
        for (Map<String, Object> frame : variableStack) {
            merged.putAll(frame);
        }
        return MapperProxy.replacePlaceholders(text, merged);
    }
}
