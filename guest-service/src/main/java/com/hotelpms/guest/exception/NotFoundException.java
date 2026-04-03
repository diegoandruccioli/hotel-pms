package com.hotelpms.guest.exception;

/**
 * Exception thrown when a requested resource is not found.
 */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new NotFoundException.
     *
     * @param message the detail message
     */
    public NotFoundException(final String message) {
        super(message);
    }
}
