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

    /**
     * Defensive copy — a mutable {@code List} field on a record is otherwise a
     * shared-reference leak.
     *
     * @param roomTypeId   the room type UUID
     * @param roomTypeName the room type display name
     * @param basePrice    the room type's fallback price
     * @param days         the resolved price for each day
     */
    public RateCalendarRow {
        days = days == null ? null : List.copyOf(days);
    }

    /**
     * Defensive accessor mirroring the compact constructor's copy.
     *
     * @return an unmodifiable copy of the days
     */
    @Override
    public List<RateCalendarDay> days() {
        return days == null ? null : List.copyOf(days);
    }
}
