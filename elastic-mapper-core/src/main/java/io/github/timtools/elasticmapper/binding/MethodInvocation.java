package io.github.timtools.elasticmapper.binding;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures a single Mapper method invocation: the method, arguments,
 * and parameter name bindings.
 */
public class MethodInvocation {

    private final Method method;
    private final Object[] args;
    private final Map<String, Object> paramMap;

    public MethodInvocation(Method method, Object[] args) {
        this.method = method;
        this.args = args != null ? args.clone() : new Object[0];
        this.paramMap = new LinkedHashMap<>();
    }

    public MethodInvocation(Method method, Object[] args, Map<String, Object> paramMap) {
        this.method = method;
        this.args = args != null ? args.clone() : new Object[0];
        this.paramMap = new LinkedHashMap<>(paramMap);
    }

    public Method getMethod() { return method; }
    public Object[] getArgs() { return args; }

    /**
     * Returns the parameter value for a given name.
     */
    public Object getParam(String name) {
        return paramMap.get(name);
    }

    /**
     * Returns all named parameters.
     */
    public Map<String, Object> getParamMap() {
        return Collections.unmodifiableMap(paramMap);
    }

    /**
     * Returns the argument at the given index.
     */
    public Object getArg(int index) {
        if (index >= 0 && index < args.length) {
            return args[index];
        }
        return null;
    }

    @Override
    public String toString() {
        return "MethodInvocation{" +
                "method=" + method.getName() +
                ", paramMap=" + paramMap +
                '}';
    }
}
