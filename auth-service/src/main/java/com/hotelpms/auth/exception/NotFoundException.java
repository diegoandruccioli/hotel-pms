package com.hotelpms.auth.exception;

/**
 * Thrown when a requested resource is not found.
 */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a NotFoundException with the given message.
     *
     * @param message the detail message
     */
    public NotFoundException(final String message) {
        super(message);
    }
}
