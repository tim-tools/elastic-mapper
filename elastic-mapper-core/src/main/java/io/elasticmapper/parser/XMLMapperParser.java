package io.elasticmapper.parser;

import io.elasticmapper.parser.node.StatementNode;
import io.elasticmapper.script.DynamicStatement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Entry point for loading and parsing XML Mapper files.
 * Scans the classpath for XML files matching the configured locations,
 * parses them into {@link DynamicStatement} trees with pre-compiled
 * OGNL expressions, and holds them for fast runtime lookup.
 */
public class XMLMapperParser {

    private static final Logger log = LoggerFactory.getLogger(XMLMapperParser.class);

    /** namespace::statementId → compiled DynamicStatement */
    private final Map<String, DynamicStatement> statements = new LinkedHashMap<>();

    /**
     * Parses a single XML mapper file from the classpath.
     *
     * @param resourcePath classpath resource path (e.g., "es-mapper/UserMapper.xml")
     */
    public void parseResource(String resourcePath) {
        log.info("Parsing XML mapper: {}", resourcePath);
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new StatementCompileException(
                    "XML mapper not found on classpath: " + resourcePath);
        }
        try {
            Map<String, StatementNode> parsed = new XMLScriptBuilder().parse(stream);
            String namespace = namespaceFromPath(resourcePath);
            for (Map.Entry<String, StatementNode> entry : parsed.entrySet()) {
                String key = namespace + "::" + entry.getKey();
                if (statements.containsKey(key)) {
                    throw new StatementCompileException(
                            "Duplicate statement id: " + key
                            + " in " + resourcePath
                            + ". Each statement id must be unique within a namespace.");
                }
                // Compile at startup — pre-parses all OGNL test expressions
                statements.put(key, DynamicStatement.compile(entry.getKey(), entry.getValue()));
            }
            log.info("Parsed {} statements from {}", parsed.size(), resourcePath);
        } finally {
            try { stream.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Looks up a compiled statement by namespace and statement ID.
     *
     * @param namespace   typically the mapper interface simple name
     * @param statementId the statement id from the XML
     * @return the compiled DynamicStatement, or null
     */
    public DynamicStatement getStatement(String namespace, String statementId) {
        return statements.get(namespace + "::" + statementId);
    }

    /**
     * Returns all parsed statements (immutable).
     */
    public Map<String, DynamicStatement> getStatements() {
        return Collections.unmodifiableMap(statements);
    }

    /**
     * Number of loaded statements.
     */
    public int size() {
        return statements.size();
    }

    /**
     * Derives a namespace from the XML resource path.
     * "es-mapper/UserMapper.xml" → "UserMapper"
     */
    static String namespaceFromPath(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
