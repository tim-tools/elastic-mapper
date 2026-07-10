package io.github.timtools.elasticmapper.metadata;

import io.github.timtools.elasticmapper.executor.ElasticMapperException;

/**
 * Thrown when entity metadata parsing fails.
 */
public class MetadataParseException extends ElasticMapperException {

    private static final String ERROR_CODE = "EM-META-001";

    public MetadataParseException(String message) {
        super(ERROR_CODE, message);
    }

    public MetadataParseException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
