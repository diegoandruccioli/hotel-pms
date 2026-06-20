package com.hotelpms.frontdesk.exception;

/**
 * Exception thrown when a call to an external service (guest-service,
 * billing-service) fails or returns an unexpected result.
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
