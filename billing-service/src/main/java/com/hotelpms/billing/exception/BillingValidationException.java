package com.hotelpms.billing.exception;

import java.io.Serial;

/**
 * Exception thrown when a validation rule in the billing process fails.
 */
public class BillingValidationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new BillingValidationException with the specified detail
     * message.
     * 
     * @param message the detail message
     */
    public BillingValidationException(final String message) {
        super(message);
    }
}
