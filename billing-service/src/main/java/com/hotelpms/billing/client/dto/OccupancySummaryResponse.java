package com.hotelpms.billing.client.dto;

import java.util.List;

/**
 * Mirrors frontdesk-service's own {@code OccupancySummaryResponse} by shape.
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
