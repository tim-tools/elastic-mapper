package io.elasticmapper.generator;

import io.elasticmapper.config.ElasticMapperConfig;
import io.elasticmapper.executor.ElasticTemplate;
import io.elasticmapper.serialize.EsTypeMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reverse-generates Java entity classes and Mapper interfaces
 * from existing Elasticsearch index mappings.
 */
public class CodeGenerator {

    private final ElasticTemplate template;

    public CodeGenerator(ElasticTemplate template) {
        this.template = template;
    }

    /**
     * Generates Java source code for an entity class from an ES index.
     *
     * @param indexName    the ES index name
     * @param packageName  target Java package
     * @param className    target class name (null to derive from index)
     * @return the generated Java source code as a string
     */
    public String generateEntity(String indexName, String packageName, String className) {
        com.google.gson.JsonObject mapping = template.performRequest("GET", "/" + indexName + "/_mapping", null);

        String entityName = (className != null) ? className : snakeToPascal(indexName);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import io.elasticmapper.annotations.*;\n");
        sb.append("import java.util.*;\n\n");
        sb.append("@IndexName(\"").append(indexName).append("\")\n");
        sb.append("public class ").append(entityName).append(" {\n\n");
        sb.append("    @Id\n");
        sb.append("    private String id;\n\n");

        // Parse properties from mapping
        if (mapping.has(indexName)) {
            com.google.gson.JsonObject indexObj = mapping.getAsJsonObject(indexName);
            if (indexObj.has("mappings")) {
                com.google.gson.JsonObject mappings = indexObj.getAsJsonObject("mappings");
                if (mappings.has("properties")) {
                    com.google.gson.JsonObject props = mappings.getAsJsonObject("properties");
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : props.entrySet()) {
                        String fieldName = entry.getKey();
                        com.google.gson.JsonObject fieldDef = entry.getValue().getAsJsonObject();
                        String esType = fieldDef.has("type") ? fieldDef.get("type").getAsString() : "object";

                        Class<?> javaType = EsTypeMapping.toJavaType(esType);
                        if (javaType == null) javaType = String.class;

                        String javaTypeStr = javaType.getSimpleName();
                        if ("nested".equals(esType)) {
                            javaTypeStr = "List<" + pascalCase(fieldName) + ">";
                        } else if ("object".equals(esType)) {
                            javaTypeStr = "Map<String, Object>";
                        }

                        sb.append("    @Field(type = \"").append(esType).append("\"");
                        if (!fieldName.equals(toCamelCase(fieldName))) {
                            sb.append(", name = \"").append(fieldName).append("\"");
                        }
                        sb.append(")\n");
                        sb.append("    private ").append(javaTypeStr).append(" ")
                                .append(toCamelCase(fieldName)).append(";\n\n");
                    }
                }
            }
        }

        // Getters and setters (basic)
        sb.append("    // Getters and Setters\n");
        sb.append("    public String getId() { return id; }\n");
        sb.append("    public void setId(String id) { this.id = id; }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Generates a Mapper interface for the given entity.
     */
    public String generateMapper(String packageName, String entityName) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import io.elasticmapper.binding.BaseMapper;\n\n");
        sb.append("public interface ").append(entityName).append("Mapper");
        sb.append(" extends BaseMapper<").append(entityName).append("> {\n");
        sb.append("    // Custom query methods go here\n");
        sb.append("}\n");
        return sb.toString();
    }

    static String toCamelCase(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') { nextUpper = true; }
            else if (nextUpper) { sb.append(Character.toUpperCase(c)); nextUpper = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }

    static String pascalCase(String snake) {
        String camel = toCamelCase(snake);
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    static String snakeToPascal(String snake) {
        return pascalCase(snake);
    }
}
