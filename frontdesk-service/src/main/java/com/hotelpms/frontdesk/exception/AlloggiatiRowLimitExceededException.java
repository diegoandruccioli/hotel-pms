package com.hotelpms.frontdesk.exception;

/**
 * Thrown when a single Alloggiati Web export would exceed the 1 000-row limit
 * imposed by the Portale Alloggiati Web upload interface.
 * The caller must split the date range or archive older stays before retrying.
 */
public class AlloggiatiRowLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the given detail message.
     *
     * @param message human-readable description including actual row count
     */
    public AlloggiatiRowLimitExceededException(final String message) {
        super(message);
    }
}
