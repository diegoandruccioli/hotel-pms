package com.hotelpms.billing.repository;

import java.math.BigDecimal;

/**
 * Spring Data interface projection backing {@link
 * InvoiceRepository#getFinancialSummaryAggregates}: the three aggregates
 * computed in SQL, without loading a single {@code Invoice} entity.
 */
public interface OwnerFinancialSummaryAggregates {

    /**
     * Returns the sum of all matching invoice amounts.
     *
     * @return the total revenue for the window
     */
    BigDecimal getTotalRevenue();

    /**
     * Returns the total number of matching invoices.
     *
     * @return the total invoice count for the window
     */
    long getTotalInvoices();

    /**
     * Returns the number of matching invoices with status {@code PAID}.
     *
     * @return the paid invoice count for the window
     */
    long getPaidInvoices();

    /**
     * Returns the sum of invoice amounts with status {@code ISSUED} — money
     * owed but not yet collected, as opposed to {@link #getTotalRevenue}
     * which sums every matching invoice regardless of status.
     *
     * @return the pending (issued, unpaid) revenue for the window
     */
    BigDecimal getPendingRevenue();
}
