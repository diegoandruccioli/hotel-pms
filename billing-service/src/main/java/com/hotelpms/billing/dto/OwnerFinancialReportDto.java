package com.hotelpms.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated financial report for the Owner/Admin Dashboard.
 *
 * @param startDate     the start of the reporting period
 * @param endDate       the end of the reporting period
 * @param totalRevenue  sum of the invoice amounts in the period, cancelled invoices excluded
 * @param totalInvoices number of invoices in the period, cancelled invoices excluded
 * @param paidInvoices  number of invoices with status PAID
 * @param invoices      list of every invoice in the period, cancelled ones included
 */
public record OwnerFinancialReportDto(
                LocalDate startDate,
                LocalDate endDate,
                BigDecimal totalRevenue,
                long totalInvoices,
                long paidInvoices,
                List<InvoiceResponse> invoices) {

        /**
         * Make a defensive copy of the invoices list to protect internal
         * representation.
         *
         * @param startDate     the start of the reporting period
         * @param endDate       the end of the reporting period
         * @param totalRevenue  sum of the invoice amounts in the period, cancelled invoices excluded
         * @param totalInvoices number of invoices in the period, cancelled invoices excluded
         * @param paidInvoices  number of invoices with status PAID
         * @param invoices      list of every invoice in the period, cancelled ones included
         */
        public OwnerFinancialReportDto {
                invoices = List.copyOf(invoices);
        }
}
