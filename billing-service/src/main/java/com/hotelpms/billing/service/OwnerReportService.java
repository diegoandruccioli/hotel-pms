package com.hotelpms.billing.service;

import com.hotelpms.billing.dto.OwnerFinancialReportDto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for generating financial reports for the Owner/Admin Dashboard.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface OwnerReportService {

    /**
     * Returns an aggregated financial report for the given date range, scoped to
     * the authenticated hotel (T-BILL-04, IDOR/cross-tenant financial data leak).
     *
     * @param hotelId   the authenticated hotel UUID; only this hotel's invoices are included
     * @param startDate the first day of the reporting period (inclusive)
     * @param endDate   the last day of the reporting period (inclusive)
     * @return aggregated report DTO
     */
    OwnerFinancialReportDto getFinancialReport(UUID hotelId, LocalDate startDate, LocalDate endDate);
}
