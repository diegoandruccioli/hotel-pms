package com.hotelpms.frontdesk.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The resolved price for a single night of a given room type.
 *
 * @param date         the night this rate applies to
 * @param nightlyPrice the resolved price for that night
 * @param rateSeasonId the {@code RateSeason} the price was resolved from, or
 *                      {@code null} when it fell back to {@code RoomType.basePrice}
 *                      (no season covers this date)
 */
public record NightlyRate(LocalDate date, BigDecimal nightlyPrice, UUID rateSeasonId) {
}
