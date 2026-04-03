package com.hotelpms.reservation.exception;

/**
 * Exception thrown when a requested resource is not found.
 */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor for NotFoundException.
     *
     * @param message the detail message
     */
    public NotFoundException(final String message) {
        super(message);
    }

    /**
     * Constructor for NotFoundException.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public NotFoundException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
