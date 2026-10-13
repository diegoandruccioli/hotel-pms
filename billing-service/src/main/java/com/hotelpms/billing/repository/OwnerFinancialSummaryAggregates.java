package com.hotelpms.billing.repository;

import java.math.BigDecimal;

/**
 * Spring Data interface projection backing {@link
 * InvoiceRepository#getFinancialSummaryAggregates}: the three aggregates
 * computed in SQL, without loading a single {@code Invoice} entity. Cancelled invoices
 * are not revenue and not issued invoices, so they count in neither.
 */
public interface OwnerFinancialSummaryAggregates {

    /**
     * Returns the sum of the matching invoice amounts, {@code CANCELLED} invoices excluded.
     *
     * @return the total revenue for the window
     */
    BigDecimal getTotalRevenue();

    /**
     * Returns the number of matching invoices, {@code CANCELLED} invoices excluded.
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
     * Returns what invoices with status {@code ISSUED} still owe: each one's total
     * minus the payments received so far (a partial payment leaves the invoice
     * {@code ISSUED}) — money owed but not yet collected.
     *
     * @return the pending (issued, unpaid) balance for the window
     */
    BigDecimal getPendingRevenue();
}
