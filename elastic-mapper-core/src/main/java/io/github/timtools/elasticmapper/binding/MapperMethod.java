package io.github.timtools.elasticmapper.binding;

import java.lang.reflect.Method;

/**
 * Describes a single method on a Mapper interface:
 * whether it's a built-in BaseMapper method or a custom statement,
 * and what statement source to use.
 */
public class MapperMethod {

    public enum Type {
        /** Built-in BaseMapper method — routed directly to ElasticTemplate. */
        BUILTIN,
        /** Custom method with {@code @Select/@Update/@Delete} annotation. */
        ANNOTATION,
        /** Custom method defined in XML mapper file. */
        XML,
        /** Not yet resolved. */
        UNRESOLVED
    }

    private final Method method;
    private final Type type;
    private final String statementSource;   // annotation value or XML content
    private final BuiltinMethod builtinMethod;

    public MapperMethod(Method method, Type type, String statementSource) {
        this.method = method;
        this.type = type;
        this.statementSource = statementSource;
        this.builtinMethod = null;
    }

    public MapperMethod(Method method, BuiltinMethod builtinMethod) {
        this.method = method;
        this.type = Type.BUILTIN;
        this.statementSource = null;
        this.builtinMethod = builtinMethod;
    }

    public Method getMethod() { return method; }
    public Type getType() { return type; }
    public String getStatementSource() { return statementSource; }
    public BuiltinMethod getBuiltinMethod() { return builtinMethod; }

    /**
     * Predefined built-in methods from BaseMapper.
     */
    public enum BuiltinMethod {
        INSERT,
        INSERT_BATCH,
        SELECT_BY_ID,
        SELECT_BY_IDS,
        SELECT_LIST,
        SELECT_PAGE,
        UPDATE_BY_ID,
        UPDATE_PARTIAL_BY_ID,
        DELETE_BY_ID,
        DELETE_BY_IDS
    }
}
