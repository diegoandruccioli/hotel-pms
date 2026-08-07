package com.hotelpms.frontdesk.stays.service.impl;

/**
 * Truncates exception messages to fit the {@code stays.*_failure_reason} database
 * columns shared by the billing, checkout-email, and Alloggiati failure-tracking
 * flows on {@link com.hotelpms.frontdesk.stays.domain.Stay}.
 */
final class StayFailureReason {

    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private StayFailureReason() {
    }

    /**
     * Truncates a failure message to fit the {@value #MAX_FAILURE_REASON_LENGTH}-char
     * column limit.
     *
     * @param message the raw exception message; may be null
     * @return a column-safe string, never longer than the column limit
     */
    static String truncate(final String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_FAILURE_REASON_LENGTH
                ? message.substring(0, MAX_FAILURE_REASON_LENGTH)
                : message;
    }
}
