package com.hotelpms.fb.exception;

import java.io.Serial;

/**
 * Thrown when a menu item is not found or does not belong to the requesting hotel.
 */
public class MenuItemNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the given message.
     *
     * @param message the detail message
     */
    public MenuItemNotFoundException(final String message) {
        super(message);
    }
}
