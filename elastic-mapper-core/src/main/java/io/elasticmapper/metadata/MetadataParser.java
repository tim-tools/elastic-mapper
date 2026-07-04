package io.elasticmapper.metadata;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.elasticmapper.annotations.Id;
import io.elasticmapper.annotations.IndexName;

/**
 * Parses entity classes to produce {@link EntityMetadata}.
 * Resolves index name and field mappings from annotations,
 * falling back to convention-based inference when annotations are absent.
 */
public final class MetadataParser {

    /** Parsed metadata is immutable — safe to cache and share across threads. */
    private static final Map<Class<?>, EntityMetadata> CACHE = new ConcurrentHashMap<>();

    private MetadataParser() {
        // utility class
    }

    /**
     * Parses an entity class into its metadata representation.
     * Results are cached — reflection occurs exactly once per entity class.
     *
     * @param entityClass the Java entity class
     * @return the parsed EntityMetadata
     * @throws MetadataParseException if the entity class cannot be parsed
     */
    public static EntityMetadata parse(Class<?> entityClass) {
        return CACHE.computeIfAbsent(entityClass, MetadataParser::parseInternal);
    }

    private static EntityMetadata parseInternal(Class<?> entityClass) {
        // 1. Resolve index name
        String indexName = resolveIndexName(entityClass);

        // 2. Scan fields
        Map<String, FieldMetadata> fields = new LinkedHashMap<>();
        FieldMetadata idField = null;

        for (Field field : entityClass.getDeclaredFields()) {
            // Skip static and transient fields
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                    java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            FieldMetadata fm = buildFieldMetadata(field);

            if (fm.isId()) {
                if (idField != null) {
                    throw new MetadataParseException(
                            "Multiple @Id fields found in " + entityClass.getName() +
                            ": " + idField.getJavaName() + " and " + fm.getJavaName() +
                            ". Only one @Id field is allowed per entity.");
                }
                idField = fm;
            }

            fields.put(fm.getJavaName(), fm);
        }

        return EntityMetadata.of(entityClass, indexName, idField, fields);
    }

    /**
     * Resolves the Elasticsearch index name for the given entity class.
     */
    static String resolveIndexName(Class<?> entityClass) {
        IndexName annotation = entityClass.getAnnotation(IndexName.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        // Convention: class name, camelCase → snake_case
        return camelToSnake(entityClass.getSimpleName());
    }

    /**
     * Builds FieldMetadata for a single field.
     */
    static FieldMetadata buildFieldMetadata(Field field) {
        field.setAccessible(true);

        String javaName = field.getName();
        Class<?> javaType = field.getType();

        io.elasticmapper.annotations.Field fieldAnn =
                field.getAnnotation(io.elasticmapper.annotations.Field.class);
        Id idAnn = field.getAnnotation(Id.class);

        // Resolve ES field name
        String esName;
        if (fieldAnn != null && !fieldAnn.name().isEmpty()) {
            esName = fieldAnn.name();
        } else {
            esName = camelToSnake(javaName);
        }

        // Resolve ES field type
        String esType;
        boolean nested = false;
        Class<?> nestedClass = null;

        if (fieldAnn != null && !fieldAnn.type().isEmpty()) {
            esType = fieldAnn.type();
            nested = "nested".equals(esType);
            if (nested) {
                nestedClass = resolveNestedClass(field);
            }
        } else {
            // Infer from Java type
            esType = inferEsType(javaType);
            if ("nested".equals(esType)) {
                nested = true;
                nestedClass = resolveNestedClass(field);
            }
        }

        // Other @Field attributes
        boolean index = fieldAnn == null || fieldAnn.index();
        String analyzer = fieldAnn != null ? fieldAnn.analyzer() : "";
        String dateFormat = fieldAnn != null ? fieldAnn.format() : "";

        return FieldMetadata.builder()
                .javaName(javaName)
                .esName(esName)
                .javaType(javaType)
                .esType(esType)
                .nested(nested)
                .id(idAnn != null)
                .nestedClass(nestedClass)
                .index(index)
                .analyzer(analyzer)
                .dateFormat(dateFormat)
                .javaField(field)
                .build();
    }

    /**
     * Infers the Elasticsearch type from a Java type.
     *
     * <table>
     *   <tr><th>Java type</th><th>ES type</th></tr>
     *   <tr><td>String</td><td>text</td></tr>
     *   <tr><td>Integer, int, Long, long, Short, short</td><td>long</td></tr>
     *   <tr><td>Float, float, Double, double, BigDecimal</td><td>double</td></tr>
     *   <tr><td>Boolean, boolean</td><td>boolean</td></tr>
     *   <tr><td>Date, LocalDate, LocalDateTime, Instant</td><td>date</td></tr>
     *   <tr><td>List&lt;T&gt;, Collection&lt;T&gt; (complex T)</td><td>nested</td></tr>
     *   <tr><td>Map&lt;String, ?&gt;</td><td>object</td></tr>
     *   <tr><td>other complex types</td><td>object</td></tr>
     * </table>
     */
    public static String inferEsType(Class<?> javaType) {
        if (javaType == null) {
            return "object";
        }

        // String
        if (String.class.equals(javaType) || char.class.equals(javaType)
                || Character.class.equals(javaType)) {
            return "text";
        }

        // Integer types → long (ES numeric)
        if (Integer.class.equals(javaType) || int.class.equals(javaType)
                || Long.class.equals(javaType) || long.class.equals(javaType)
                || Short.class.equals(javaType) || short.class.equals(javaType)
                || Byte.class.equals(javaType) || byte.class.equals(javaType)) {
            return "long";
        }

        // Floating point → double
        if (Float.class.equals(javaType) || float.class.equals(javaType)
                || Double.class.equals(javaType) || double.class.equals(javaType)
                || BigDecimal.class.equals(javaType)) {
            return "double";
        }

        // Boolean
        if (Boolean.class.equals(javaType) || boolean.class.equals(javaType)) {
            return "boolean";
        }

        // Date / time types
        if (Date.class.equals(javaType)
                || LocalDate.class.equals(javaType)
                || LocalDateTime.class.equals(javaType)
                || Instant.class.equals(javaType)
                || java.sql.Date.class.equals(javaType)
                || java.sql.Timestamp.class.equals(javaType)) {
            return "date";
        }

        // Collections → nested
        if (List.class.isAssignableFrom(javaType)
                || Collection.class.isAssignableFrom(javaType)
                || Iterable.class.isAssignableFrom(javaType)) {
            return "nested";
        }

        if (javaType.isArray()) {
            return "nested";
        }

        // Maps → object
        if (Map.class.isAssignableFrom(javaType)) {
            return "object";
        }

        // Enum → keyword
        if (javaType.isEnum()) {
            return "keyword";
        }

        // Anything else → object
        return "object";
    }

    /**
     * Attempts to resolve the nested element class from a collection field.
     */
    static Class<?> resolveNestedClass(Field field) {
        Class<?> javaType = field.getType();

        // Array type
        if (javaType.isArray()) {
            return javaType.getComponentType();
        }

        // Parameterized collection type
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                return (Class<?>) typeArgs[0];
            }
        }

        return Object.class;
    }

    /**
     * Converts camelCase to snake_case.
     * <pre>
     *   "userName" → "user_name"
     *   "HTTPHeader" → "http_header"
     *   "User" → "user"
     * </pre>
     */
    public static String camelToSnake(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                // Only insert underscore when:
                // - not the first character, AND
                // - the previous char is not already uppercase (handles acronyms like "HTTP"),
                //   OR the next char is lowercase (transition from acronym to word)
                if (i > 0) {
                    char prev = input.charAt(i - 1);
                    char next = (i + 1 < input.length()) ? input.charAt(i + 1) : 0;
                    // Insert _ if previous was lowercase, or if this is a transition
                    // from uppercase acronym to a new uppercase-starting word
                    if (Character.isLowerCase(prev) ||
                            (Character.isUpperCase(prev) && Character.isLowerCase(next))) {
                        sb.append('_');
                    }
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Converts snake_case to camelCase.
     * <pre>
     *   "user_name" → "userName"
     *   "http_header" → "httpHeader"
     * </pre>
     */
    public static String snakeToCamel(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
