package com.hotelpms.frontdesk.dashboard.dto;

import java.util.List;

/**
 * Room inventory size plus a bucketed occupied-room-nights trend for a date
 * range, scoped to the caller's hotel. Consumed by billing-service's KPI
 * report (T-GW-independent, no security-sensitive data here) to compute
 * RevPAR/ADR/Occupancy without frontdesk-service ever handling revenue
 * figures.
 *
 * @param totalRooms the hotel's current active room count
 * @param periods    occupied room-nights per time bucket, ordered by {@code periodStart}
 */
public record OccupancySummaryResponse(int totalRooms, List<OccupancyPeriodResponse> periods) {

    /**
     * Defensive copy — a caller must not be able to mutate the list backing
     * this response after construction.
     *
     * @param totalRooms the hotel's current active room count
     * @param periods    occupied room-nights per time bucket
     */
    public OccupancySummaryResponse {
        periods = List.copyOf(periods);
    }
}
