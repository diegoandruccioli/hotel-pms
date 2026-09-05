package com.hotelpms.frontdesk.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * billing-service's cash-closing summary for a single business date, as seen
 * by frontdesk-service's night audit (see {@code BillingClient#getPaymentSummary}).
 *
 * @param date       the business date this summary covers
 * @param byMethod   totals grouped by payment method
 * @param grandTotal sum of every {@code byMethod} total
 */
public record PaymentSummaryClientResponse(LocalDate date, List<PaymentMethodTotalDto> byMethod, BigDecimal grandTotal) {

    /**
     * Defensively copies {@code byMethod}.
     */
    public PaymentSummaryClientResponse {
        byMethod = byMethod == null ? List.of() : List.copyOf(byMethod);
    }
}
