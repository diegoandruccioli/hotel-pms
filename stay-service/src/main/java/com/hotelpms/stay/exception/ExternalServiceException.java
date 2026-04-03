package com.hotelpms.stay.exception;

/**
 * Exception thrown when an external service call fails.
 */
public class ExternalServiceException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ExternalServiceException with the specified detail message.
     *
     * @param message the detail message
     */
    public ExternalServiceException(final String message) {
        super(message);
    }

    /**
     * Constructs a new ExternalServiceException with the specified detail message
     * and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public ExternalServiceException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
