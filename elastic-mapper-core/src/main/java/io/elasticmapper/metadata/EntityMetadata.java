package io.elasticmapper.metadata;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata describing the mapping between a Java entity class
 * and an Elasticsearch index.
 */
public class EntityMetadata {

    /** The entity class. */
    private final Class<?> entityClass;

    /** The Elasticsearch index name. */
    private final String indexName;

    /** Metadata for the document ID field. */
    private final FieldMetadata idField;

    /** All field mappings, keyed by Java field name. */
    private final Map<String, FieldMetadata> fields;

    private EntityMetadata(Class<?> entityClass, String indexName,
                           FieldMetadata idField, Map<String, FieldMetadata> fields) {
        this.entityClass = entityClass;
        this.indexName = indexName;
        this.idField = idField;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    // ── Getters ──

    public Class<?> getEntityClass() { return entityClass; }
    public String getIndexName() { return indexName; }

    /**
     * Returns the document ID field metadata, or null if none was found.
     */
    public FieldMetadata getIdField() { return idField; }

    /**
     * Returns all field mappings, keyed by Java field name.
     */
    public Map<String, FieldMetadata> getFields() { return fields; }

    /**
     * Looks up field metadata by Java field name.
     */
    public FieldMetadata getField(String javaFieldName) {
        return fields.get(javaFieldName);
    }

    /**
     * Looks up field metadata by ES field name.
     */
    public FieldMetadata getFieldByEsName(String esFieldName) {
        for (FieldMetadata fm : fields.values()) {
            if (fm.getEsName().equals(esFieldName)) {
                return fm;
            }
        }
        return null;
    }

    /**
     * Returns the ES field name for a Java field name, or null if not found.
     */
    public String getEsFieldName(String javaFieldName) {
        FieldMetadata fm = fields.get(javaFieldName);
        return fm != null ? fm.getEsName() : null;
    }

    @Override
    public String toString() {
        return "EntityMetadata{" +
                "entityClass=" + entityClass.getName() +
                ", indexName='" + indexName + '\'' +
                ", idField=" + (idField != null ? idField.getJavaName() : "null") +
                ", fieldCount=" + fields.size() +
                '}';
    }

    /**
     * Creates a new EntityMetadata.
     */
    public static EntityMetadata of(Class<?> entityClass, String indexName,
                                    FieldMetadata idField, Map<String, FieldMetadata> fields) {
        return new EntityMetadata(entityClass, indexName, idField, fields);
    }
}
