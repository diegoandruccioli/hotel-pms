package com.hotelpms.billing.service;

import com.hotelpms.billing.dto.OwnerFinancialReportDto;
import com.hotelpms.billing.dto.OwnerFinancialSummaryDto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for generating financial reports for the Owner/Admin Dashboard.
 */
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

    /**
     * Returns the same totals as {@link #getFinancialReport}, without the
     * per-invoice list, computed entirely in SQL.
     *
     * @param hotelId   the authenticated hotel UUID; only this hotel's invoices are included
     * @param startDate the first day of the reporting period (inclusive)
     * @param endDate   the last day of the reporting period (inclusive)
     * @return the aggregated totals
     */
    OwnerFinancialSummaryDto getFinancialSummary(UUID hotelId, LocalDate startDate, LocalDate endDate);

    /**
     * Streams the same invoice list as {@link #getFinancialReport} to {@code out} as
     * CSV -- server-side replacement for the client-side CSV generation previously done
     * in the frontend, scoped to the authenticated hotel (T-BILL-04).
     *
     * @param hotelId   the authenticated hotel UUID; only this hotel's invoices are included
     * @param startDate the first day of the reporting period (inclusive)
     * @param endDate   the last day of the reporting period (inclusive)
     * @param out       the stream to write CSV bytes to
     * @throws java.io.IOException if writing to {@code out} fails
     */
    void exportFinancialReportCsv(UUID hotelId, LocalDate startDate, LocalDate endDate, java.io.OutputStream out)
            throws java.io.IOException;
}
