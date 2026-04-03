package com.hotelpms.auth.exception;

/**
 * Exception thrown when authentication fails due to bad credentials.
 */
public class BadCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new BadCredentialsException with the specified message.
     *
     * @param message the detail message
     */
    public BadCredentialsException(final String message) {
        super(message);
    }
}
