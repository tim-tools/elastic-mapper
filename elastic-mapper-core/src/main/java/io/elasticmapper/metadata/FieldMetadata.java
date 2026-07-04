package io.elasticmapper.metadata;

import java.lang.reflect.Field;

/**
 * Metadata describing a single field mapping between a Java class field
 * and an Elasticsearch document field.
 */
public class FieldMetadata {

    /** The Java field name (camelCase). */
    private final String javaName;

    /** The Elasticsearch field name (typically snake_case). */
    private final String esName;

    /** The Java field type. */
    private final Class<?> javaType;

    /** The Elasticsearch field type (keyword, text, long, nested, etc.). */
    private final String esType;

    /** Whether this field stores nested objects. */
    private final boolean nested;

    /** Whether this field is the document ID. */
    private final boolean id;

    /** The nested element type when isNested is true, otherwise null. */
    private final Class<?> nestedClass;

    /** Whether this field is indexed. */
    private final boolean index;

    /** Custom analyzer name, empty if not specified. */
    private final String analyzer;

    /** Date format pattern, empty if not specified. */
    private final String dateFormat;

    /** The underlying Java reflection Field. */
    private final Field javaField;

    private FieldMetadata(Builder builder) {
        this.javaName = builder.javaName;
        this.esName = builder.esName;
        this.javaType = builder.javaType;
        this.esType = builder.esType;
        this.nested = builder.nested;
        this.id = builder.id;
        this.nestedClass = builder.nestedClass;
        this.index = builder.index;
        this.analyzer = builder.analyzer;
        this.dateFormat = builder.dateFormat;
        this.javaField = builder.javaField;
    }

    // ── Getters ──

    public String getJavaName() { return javaName; }
    public String getEsName() { return esName; }
    public Class<?> getJavaType() { return javaType; }
    public String getEsType() { return esType; }
    public boolean isNested() { return nested; }
    public boolean isId() { return id; }
    public Class<?> getNestedClass() { return nestedClass; }
    public boolean isIndex() { return index; }
    public String getAnalyzer() { return analyzer; }
    public String getDateFormat() { return dateFormat; }
    public Field getJavaField() { return javaField; }

    @Override
    public String toString() {
        return "FieldMetadata{" +
                "javaName='" + javaName + '\'' +
                ", esName='" + esName + '\'' +
                ", esType='" + esType + '\'' +
                ", isId=" + id +
                ", isNested=" + nested +
                '}';
    }

    /**
     * Builder for constructing FieldMetadata instances.
     */
    public static class Builder {
        private String javaName;
        private String esName;
        private Class<?> javaType;
        private String esType;
        private boolean nested;
        private boolean id;
        private Class<?> nestedClass;
        private boolean index = true;
        private String analyzer = "";
        private String dateFormat = "";
        private Field javaField;

        public Builder javaName(String javaName) { this.javaName = javaName; return this; }
        public Builder esName(String esName) { this.esName = esName; return this; }
        public Builder javaType(Class<?> javaType) { this.javaType = javaType; return this; }
        public Builder esType(String esType) { this.esType = esType; return this; }
        public Builder nested(boolean nested) { this.nested = nested; return this; }
        public Builder id(boolean id) { this.id = id; return this; }
        public Builder nestedClass(Class<?> nestedClass) { this.nestedClass = nestedClass; return this; }
        public Builder index(boolean index) { this.index = index; return this; }
        public Builder analyzer(String analyzer) { this.analyzer = analyzer; return this; }
        public Builder dateFormat(String dateFormat) { this.dateFormat = dateFormat; return this; }
        public Builder javaField(Field javaField) { this.javaField = javaField; return this; }

        public FieldMetadata build() {
            return new FieldMetadata(this);
        }
    }

    /**
     * Creates a new Builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
}
