package com.hotelpms.reservation.exception;

/**
 * Exception thrown when an external service call fails.
 */
public class ExternalServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor for ExternalServiceException.
     *
     * @param message the detail message
     */
    public ExternalServiceException(final String message) {
        super(message);
    }

    /**
     * Constructor for ExternalServiceException.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public ExternalServiceException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
