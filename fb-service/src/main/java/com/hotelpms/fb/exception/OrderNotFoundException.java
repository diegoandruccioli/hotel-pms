package com.hotelpms.fb.exception;

/**
 * Exception thrown when a restaurant order cannot be found.
 */
public class OrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new OrderNotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public OrderNotFoundException(final String message) {
        super(message);
    }
}
