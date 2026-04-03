package com.hotelpms.billing.exception;

import java.io.Serial;

/**
 * Exception thrown when an external service call fails.
 */
public class ExternalServiceException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ExternalServiceException with the specified detail message.
     * 
     * @param message the detail message
     */
    public ExternalServiceException(final String message) {
        super(message);
    }
}
