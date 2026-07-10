package io.github.timtools.elasticmapper.parser;

import io.github.timtools.elasticmapper.executor.ElasticMapperException;

/**
 * Thrown when an XML mapper statement fails to compile.
 * This is typically thrown at application startup to fail-fast.
 */
public class StatementCompileException extends ElasticMapperException {

    public StatementCompileException(String message) {
        super("EM-SQL-001", message);
    }

    public StatementCompileException(String message, Throwable cause) {
        super("EM-SQL-001", message, cause);
    }
}
