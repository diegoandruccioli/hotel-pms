package com.hotelpms.frontdesk.pricing.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating/updating a {@code RateSeason}. {@code roomTypeId} is
 * taken from the path, not this body — a season always belongs to exactly one
 * room type and the URL is the single source of truth for it (same convention
 * as nested resources elsewhere in this service).
 *
 * @param name         optional display label (e.g. "Alta stagione")
 * @param startDate    the first date this season applies to (inclusive)
 * @param endDate      the last date this season applies to (inclusive)
 * @param nightlyPrice the price per night during [startDate, endDate]
 */
public record RateSeasonRequest(
        @Size(max = 100) String name,

        @NotNull(message = "Required") LocalDate startDate,

        @NotNull(message = "Required") LocalDate endDate,

        @NotNull(message = "Required") @Positive BigDecimal nightlyPrice) {

    /**
     * Mirrors the DB-level {@code chk_rate_seasons_dates} constraint
     * ({@code end_date >= start_date}) at the API boundary, so an inverted
     * range is rejected with a clear 400 instead of surfacing as a generic
     * data-integrity 409 from the database check constraint.
     *
     * @return {@code true} if the range is valid or either date is null
     *         (null-checking is delegated to the individual {@code @NotNull}
     *         field constraints)
     */
    @AssertTrue(message = "endDate must not be before startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
