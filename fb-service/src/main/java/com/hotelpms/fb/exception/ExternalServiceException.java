package com.hotelpms.fb.exception;

/**
 * Exception thrown when an external service call fails.
 */
public class ExternalServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ExternalServiceException with the specified message.
     *
     * @param message the detail message
     */
    public ExternalServiceException(final String message) {
        super(message);
    }
}
