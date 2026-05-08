package com.hotelpms.stay.exception;

/**
 * Thrown when stay data fails Alloggiati Web domain validation before report generation.
 * Examples: FAMILIARE without CAPOFAMIGLIA, incoherent group structure,
 * check-out date before check-in date.
 */
public class AlloggiatiValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the given detail message.
     *
     * @param message key-prefixed message (e.g. {@code "ALLOGGIATI_FAMILIARE_WITHOUT_CAPO: ..."})
     */
    public AlloggiatiValidationException(final String message) {
        super(message);
    }
}
