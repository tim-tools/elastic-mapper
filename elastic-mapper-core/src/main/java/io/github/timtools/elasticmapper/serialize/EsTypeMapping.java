package io.github.timtools.elasticmapper.serialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Bidirectional mapping between Elasticsearch field types and Java types.
 * Used by both the metadata parser (Java → ES) and the code generator (ES → Java).
 */
public final class EsTypeMapping {

    private EsTypeMapping() {
        // utility class
    }

    // ── Java → ES (used by MetadataParser) ──

    private static final Map<Class<?>, String> JAVA_TO_ES = new HashMap<>();

    static {
        JAVA_TO_ES.put(String.class, "text");
        JAVA_TO_ES.put(char.class, "text");
        JAVA_TO_ES.put(Character.class, "text");

        JAVA_TO_ES.put(Integer.class, "long");
        JAVA_TO_ES.put(int.class, "long");
        JAVA_TO_ES.put(Long.class, "long");
        JAVA_TO_ES.put(long.class, "long");
        JAVA_TO_ES.put(Short.class, "long");
        JAVA_TO_ES.put(short.class, "long");
        JAVA_TO_ES.put(Byte.class, "long");
        JAVA_TO_ES.put(byte.class, "long");

        JAVA_TO_ES.put(Float.class, "double");
        JAVA_TO_ES.put(float.class, "double");
        JAVA_TO_ES.put(Double.class, "double");
        JAVA_TO_ES.put(double.class, "double");
        JAVA_TO_ES.put(BigDecimal.class, "double");

        JAVA_TO_ES.put(Boolean.class, "boolean");
        JAVA_TO_ES.put(boolean.class, "boolean");

        JAVA_TO_ES.put(Date.class, "date");
        JAVA_TO_ES.put(LocalDate.class, "date");
        JAVA_TO_ES.put(LocalDateTime.class, "date");
        JAVA_TO_ES.put(Instant.class, "date");
        JAVA_TO_ES.put(java.sql.Date.class, "date");
        JAVA_TO_ES.put(java.sql.Timestamp.class, "date");
    }

    // ── ES → Java (used by code generator) ──

    private static final Map<String, Class<?>> ES_TO_JAVA = new HashMap<>();

    static {
        ES_TO_JAVA.put("text", String.class);
        ES_TO_JAVA.put("keyword", String.class);
        ES_TO_JAVA.put("long", Long.class);
        ES_TO_JAVA.put("integer", Integer.class);
        ES_TO_JAVA.put("short", Short.class);
        ES_TO_JAVA.put("byte", Byte.class);
        ES_TO_JAVA.put("double", Double.class);
        ES_TO_JAVA.put("float", Float.class);
        ES_TO_JAVA.put("half_float", Float.class);
        ES_TO_JAVA.put("scaled_float", Double.class);
        ES_TO_JAVA.put("boolean", Boolean.class);
        ES_TO_JAVA.put("date", LocalDateTime.class);
        ES_TO_JAVA.put("date_nanos", LocalDateTime.class);
        ES_TO_JAVA.put("binary", String.class);
        ES_TO_JAVA.put("object", Map.class);
        ES_TO_JAVA.put("flattened", Map.class);
        ES_TO_JAVA.put("geo_point", String.class);
        ES_TO_JAVA.put("geo_shape", String.class);
        ES_TO_JAVA.put("ip", String.class);
        ES_TO_JAVA.put("completion", String.class);
        ES_TO_JAVA.put("nested", java.util.List.class);
    }

    /**
     * Returns the ES type for a given Java class, or null if not found.
     * Does NOT handle collections/nested — use {@code MetadataParser.inferEsType} for that.
     */
    public static String toEsType(Class<?> javaType) {
        return JAVA_TO_ES.get(javaType);
    }

    /**
     * Returns the Java class for a given ES type, or null if not found.
     */
    public static Class<?> toJavaType(String esType) {
        return ES_TO_JAVA.get(esType);
    }
}
