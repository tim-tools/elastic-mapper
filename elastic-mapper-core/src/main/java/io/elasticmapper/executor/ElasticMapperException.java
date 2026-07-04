package io.elasticmapper.executor;

/**
 * Base exception for all ElasticMapper runtime errors.
 */
public class ElasticMapperException extends RuntimeException {

    /**
     * Error code following the "EM-XXX-NNN" convention.
     */
    private final String errorCode;

    public ElasticMapperException(String errorCode, String message) {
        super("[" + errorCode + "] " + message);
        this.errorCode = errorCode;
    }

    public ElasticMapperException(String errorCode, String message, Throwable cause) {
        super("[" + errorCode + "] " + message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
