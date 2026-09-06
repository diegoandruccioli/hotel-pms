package com.hotelpms.frontdesk.dashboard.dto;

import java.time.LocalDate;

/**
 * Occupied room-nights for one time bucket of an occupancy trend report.
 * Counted night-by-night: both {@code CHECKED_IN} (still in-house) and
 * {@code CHECKED_OUT} stays contribute one occupied room-night for every
 * calendar night in this bucket their stay actually covers, regardless of
 * which bucket their arrival date falls in.
 *
 * @param periodStart         the start of this time bucket
 * @param occupiedRoomNights  nights actually occupied within this bucket
 */
public record OccupancyPeriodResponse(LocalDate periodStart, long occupiedRoomNights) {
}
