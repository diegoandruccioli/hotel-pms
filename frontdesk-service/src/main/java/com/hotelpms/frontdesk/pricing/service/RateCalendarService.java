package com.hotelpms.frontdesk.pricing.service;

import com.hotelpms.frontdesk.pricing.dto.RateBulkApplyRequest;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarResponse;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads and bulk-writes the rate calendar grid: every room type's resolved
 * nightly price across a date range, and applying one price to a range across
 * several room types at once. Distinct from {@link RateSeasonAdminService},
 * which manages one season at a time for one room type; this is the
 * whole-hotel, whole-range view a "Tariffe" screen needs.
 */
public interface RateCalendarService {

    /**
     * Builds the rate calendar grid for every room type in the hotel, over
     * {@code [startDate, endDate]} (both inclusive).
     *
     * @param hotelId   the authenticated hotel UUID
     * @param startDate the range start (inclusive)
     * @param endDate   the range end (inclusive)
     * @return one row per room type, one resolved price per day
     */
    RateCalendarResponse getCalendar(UUID hotelId, LocalDate startDate, LocalDate endDate);

    /**
     * Applies one nightly price to a date range across several room types,
     * splitting or trimming any existing season each room type's range
     * collides with (see the implementation for the exact split/trim rules).
     *
     * @param hotelId the authenticated hotel UUID
     * @param request the room types, range and price to apply
     * @return the newly created season for each room type, in request order
     */
    List<RateSeasonResponse> bulkApply(UUID hotelId, RateBulkApplyRequest request);
}
