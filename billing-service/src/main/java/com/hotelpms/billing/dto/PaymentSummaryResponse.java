package com.hotelpms.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cash-closing summary for a single business date, consumed by
 * frontdesk-service's night audit. {@code byMethod} lists only methods that
 * had at least one payment recorded that day — a method with no activity
 * simply doesn't appear, rather than showing a zero row.
 *
 * @param date      the business date this summary covers
 * @param byMethod  totals grouped by payment method
 * @param grandTotal sum of every {@code byMethod} total
 */
public record PaymentSummaryResponse(LocalDate date, List<PaymentMethodTotalResponse> byMethod, BigDecimal grandTotal) {

    /**
     * Defensively copies {@code byMethod} so the record can't expose or be
     * mutated through the caller's original list reference.
     */
    public PaymentSummaryResponse {
        byMethod = List.copyOf(byMethod);
    }
}
