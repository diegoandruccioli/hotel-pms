package com.hotelpms.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aggregated-only financial summary for the given date range — the same
 * totals as {@link OwnerFinancialReportDto} without the per-invoice list.
 * Backs the Dashboard's occupancy/revenue widget, which only ever needed
 * these three numbers but previously triggered a full {@code /reports/owner}
 * call (every invoice ever issued, unpaginated) just to sum them client-side.
 *
 * @param startDate      the start of the reporting period
 * @param endDate        the end of the reporting period
 * @param totalRevenue   sum of all invoice amounts in the period, regardless of status
 * @param totalInvoices  total number of invoices in the period
 * @param paidInvoices   number of invoices with status PAID
 * @param pendingRevenue sum of invoice amounts with status ISSUED — owed but not yet collected
 */
public record OwnerFinancialSummaryDto(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalRevenue,
        long totalInvoices,
        long paidInvoices,
        BigDecimal pendingRevenue) {
}
