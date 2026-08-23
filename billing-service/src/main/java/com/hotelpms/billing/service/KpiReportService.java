package com.hotelpms.billing.service;

import com.hotelpms.billing.dto.KpiReportDto;
import com.hotelpms.billing.dto.ReportGranularity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for computing the RevPAR/ADR/Occupancy trend report (epic C4).
 */
@FunctionalInterface
public interface KpiReportService {

    /**
     * Computes the KPI trend for a date range, scoped to a hotel.
     *
     * @param hotelId     the hotel UUID (tenant isolation)
     * @param startDate   start of the period (inclusive)
     * @param endDate     end of the period (inclusive)
     * @param granularity the time-bucket size
     * @return the KPI report
     */
    KpiReportDto getKpiReport(UUID hotelId, LocalDate startDate, LocalDate endDate, ReportGranularity granularity);
}
