package com.hotelpms.frontdesk.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for a {@code RateSeason}.
 *
 * @param id           the season UUID
 * @param roomTypeId   the room type this season applies to
 * @param name         optional display label
 * @param startDate    the first date this season applies to (inclusive)
 * @param endDate      the last date this season applies to (inclusive)
 * @param nightlyPrice the price per night during [startDate, endDate]
 */
public record RateSeasonResponse(
        UUID id,
        UUID roomTypeId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal nightlyPrice) {
}
