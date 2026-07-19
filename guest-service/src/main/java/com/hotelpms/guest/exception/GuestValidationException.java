package com.hotelpms.guest.exception;

/**
 * Thrown when a guest field fails a business validation rule that cannot be
 * expressed as a Jakarta Bean Validation annotation — e.g. a Comune/Provincia
 * pair that doesn't match any real Italian municipality (P0-1).
 */
public class GuestValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new GuestValidationException with the given detail message.
     *
     * @param message the detail message
     */
    public GuestValidationException(final String message) {
        super(message);
    }
}
