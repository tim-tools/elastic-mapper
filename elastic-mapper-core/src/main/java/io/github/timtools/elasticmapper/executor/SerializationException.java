package io.github.timtools.elasticmapper.executor;

/**
 * Thrown when JSON serialization or deserialization fails.
 */
public class SerializationException extends ElasticMapperException {

    public SerializationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public SerializationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
