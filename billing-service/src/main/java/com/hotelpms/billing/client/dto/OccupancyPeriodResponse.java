package com.hotelpms.billing.client.dto;

import java.time.LocalDate;

/**
 * Mirrors frontdesk-service's own {@code OccupancyPeriodResponse} by shape —
 * the two modules have no shared DTO layer for internal client contracts
 * (same convention as every other Feign response record in this package).
 *
 * @param periodStart        the start of this time bucket
 * @param occupiedRoomNights nights actually stayed, attributed to this bucket
 */
public record OccupancyPeriodResponse(LocalDate periodStart, long occupiedRoomNights) {
}
