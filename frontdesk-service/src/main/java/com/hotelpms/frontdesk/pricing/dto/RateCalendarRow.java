package com.hotelpms.frontdesk.pricing.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One room type's row in the rate calendar grid: its base price plus a
 * resolved price for every day in the requested range.
 *
 * @param roomTypeId   the room type UUID
 * @param roomTypeName the room type display name
 * @param basePrice    the room type's fallback price (used for any day not
 *                     covered by an active season)
 * @param days         the resolved price for each day in the requested range,
 *                     in date order
 */
public record RateCalendarRow(
        UUID roomTypeId,
        String roomTypeName,
        BigDecimal basePrice,
        List<RateCalendarDay> days) {
}
