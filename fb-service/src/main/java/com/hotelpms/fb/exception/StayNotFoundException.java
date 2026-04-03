package com.hotelpms.fb.exception;

/**
 * Exception thrown when a requested stay is not found.
 */
public class StayNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new StayNotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public StayNotFoundException(final String message) {
        super(message);
    }

    /**
     * Constructs a new StayNotFoundException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public StayNotFoundException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
