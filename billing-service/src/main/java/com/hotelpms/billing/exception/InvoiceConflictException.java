package com.hotelpms.billing.exception;

import java.io.Serial;

/**
 * Exception thrown when an operation conflicts with the current state of an invoice
 * (e.g. duplicate stay invoice, charge on a closed invoice).
 */
public class InvoiceConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new InvoiceConflictException with the specified detail message.
     *
     * @param message the detail message
     */
    public InvoiceConflictException(final String message) {
        super(message);
    }
}
