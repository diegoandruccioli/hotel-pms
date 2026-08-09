package com.hotelpms.frontdesk.pricing.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request to apply one nightly price to a date range across several room
 * types at once — the "drag-select a range on the rate calendar and apply a
 * price" operation. Unlike {@link RateSeasonRequest}, this does not fail on
 * an overlap with an existing season: it splits/trims whatever seasons the
 * new range collides with, per room type (see
 * {@code RateCalendarServiceImpl.applySplitTrim}).
 *
 * @param roomTypeIds  the room types to apply the price to
 * @param startDate    the first date this price applies to (inclusive)
 * @param endDate      the last date this price applies to (inclusive)
 * @param nightlyPrice the price per night during [startDate, endDate]
 * @param name         optional display label (e.g. "Alta stagione"), applied
 *                     to every created season
 */
public record RateBulkApplyRequest(
        @NotEmpty(message = REQUIRED_MSG) List<UUID> roomTypeIds,

        @NotNull(message = REQUIRED_MSG) LocalDate startDate,

        @NotNull(message = REQUIRED_MSG) LocalDate endDate,

        @NotNull(message = REQUIRED_MSG) @Positive BigDecimal nightlyPrice,

        @Size(max = 100) String name) {

    /** Shared validation message for required fields. */
    static final String REQUIRED_MSG = "Required";

    /**
     * Defensive copy — a mutable {@code List} field on a record is otherwise a
     * shared-reference leak (same convention as {@code QuotationRequest.roomIds}).
     *
     * @param roomTypeIds the room type ids
     * @param startDate   the range start
     * @param endDate     the range end
     * @param nightlyPrice the nightly price
     * @param name        the optional display label
     */
    public RateBulkApplyRequest {
        roomTypeIds = roomTypeIds == null ? null : List.copyOf(roomTypeIds);
    }

    /**
     * Mirrors {@code RateSeasonRequest.isDateRangeValid} at the API boundary.
     *
     * @return {@code true} if the range is valid or either date is null
     */
    @AssertTrue(message = "endDate must not be before startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    /**
     * Defensive accessor mirroring the compact constructor's copy.
     *
     * @return an unmodifiable copy of the room type ids
     */
    @Override
    public List<UUID> roomTypeIds() {
        return roomTypeIds == null ? null : List.copyOf(roomTypeIds);
    }
}
