package com.hotelpms.billing.exception;

import java.io.Serial;

/**
 * Exception thrown when a requested resource is not found.
 */
public class NotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new NotFoundException with the specified detail message.
     * 
     * @param message the detail message
     */
    public NotFoundException(final String message) {
        super(message);
    }
}
