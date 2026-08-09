package com.hotelpms.frontdesk.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single day's resolved price for one room type in the rate calendar grid.
 *
 * @param date         the calendar date
 * @param price        the effective nightly price for this date (season price,
 *                     or {@code RoomType.basePrice} when no season covers it)
 * @param rateSeasonId the covering season's id, or {@code null} when the price
 *                     is the room type's base price (no season covers this date)
 * @param seasonName   the covering season's display name, or {@code null} when
 *                     {@code rateSeasonId} is {@code null}
 */
public record RateCalendarDay(
        LocalDate date,
        BigDecimal price,
        UUID rateSeasonId,
        String seasonName) {
}
