package com.hotelpms.frontdesk.dashboard.service;

import com.hotelpms.frontdesk.dashboard.dto.OccupancySummaryResponse;
import com.hotelpms.frontdesk.dashboard.dto.ReportGranularity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for computing the occupancy trend summary backing the KPI report
 * (epic C4).
 */
@FunctionalInterface
public interface OccupancySummaryService {

    /**
     * Computes the room count and bucketed occupied-room-nights trend for a
     * date range, scoped to a hotel.
     *
     * @param dateFrom    start of the window (inclusive)
     * @param dateTo      end of the window (exclusive)
     * @param granularity the time-bucket size
     * @param hotelId     the hotel UUID (tenant isolation)
     * @return the occupancy summary
     */
    OccupancySummaryResponse getOccupancySummary(
            LocalDate dateFrom, LocalDate dateTo, ReportGranularity granularity, UUID hotelId);
}
