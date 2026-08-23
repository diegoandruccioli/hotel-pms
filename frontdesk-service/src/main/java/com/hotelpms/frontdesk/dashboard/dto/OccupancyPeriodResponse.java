package com.hotelpms.frontdesk.dashboard.dto;

import java.time.LocalDate;

/**
 * Occupied room-nights for one time bucket of an occupancy trend report.
 * Only {@code CHECKED_OUT} stays are counted, attributed to the bucket of
 * their {@code actualCheckInTime} — the same arrival-date convention the
 * day-sheet already uses. A still-{@code CHECKED_IN} stay contributes
 * nothing until it checks out, so a bucket covering an in-progress period is
 * an undercount by design rather than a guess at future occupancy.
 *
 * @param periodStart         the start of this time bucket
 * @param occupiedRoomNights  nights actually stayed, attributed to this bucket
 */
public record OccupancyPeriodResponse(LocalDate periodStart, long occupiedRoomNights) {
}
