package com.hotelpms.billing.service;

import com.hotelpms.billing.dto.OwnerFinancialReportDto;

import java.time.LocalDate;

/**
 * Service for generating financial reports for the Owner/Admin Dashboard.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface OwnerReportService {

    /**
     * Returns an aggregated financial report for the given date range.
     *
     * @param startDate the first day of the reporting period (inclusive)
     * @param endDate   the last day of the reporting period (inclusive)
     * @return aggregated report DTO
     */
    OwnerFinancialReportDto getFinancialReport(LocalDate startDate, LocalDate endDate);
}
