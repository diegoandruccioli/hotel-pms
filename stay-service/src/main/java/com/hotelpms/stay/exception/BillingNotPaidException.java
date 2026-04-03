package com.hotelpms.stay.exception;

/**
 * Thrown when a check-out is attempted but the billing folio is not fully paid.
 */
public class BillingNotPaidException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs the exception with a message.
     *
     * @param message the detail message
     */
    public BillingNotPaidException(final String message) {
        super(message);
    }
}
