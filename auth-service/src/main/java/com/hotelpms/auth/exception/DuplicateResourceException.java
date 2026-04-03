package com.hotelpms.auth.exception;

/**
 * Exception thrown when a resource conflict occurs (e.g., duplicate username).
 */
public class DuplicateResourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new DuplicateResourceException with the specified message.
     *
     * @param message the detail message
     */
    public DuplicateResourceException(final String message) {
        super(message);
    }
}
