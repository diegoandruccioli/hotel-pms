package com.hotelpms.fb.exception;

/**
 * Exception thrown when a restaurant order fails business validation.
 */
public class OrderValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new OrderValidationException with the specified message.
     *
     * @param message the detail message
     */
    public OrderValidationException(final String message) {
        super(message);
    }
}
