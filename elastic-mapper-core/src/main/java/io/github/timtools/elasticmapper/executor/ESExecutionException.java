package io.github.timtools.elasticmapper.executor;

/**
 * Thrown when an Elasticsearch operation fails (communication error, timeout, 4xx/5xx).
 */
public class ESExecutionException extends ElasticMapperException {

    /** HTTP status code, or -1 if not an HTTP error. */
    private final int statusCode;

    public ESExecutionException(String errorCode, String message, int statusCode, Throwable cause) {
        super(errorCode, message, cause);
        this.statusCode = statusCode;
    }

    public ESExecutionException(String errorCode, String message, int statusCode) {
        super(errorCode, message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
