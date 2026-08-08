package com.hotelpms.frontdesk.pricing.service;

import com.hotelpms.frontdesk.pricing.dto.NightlyRate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resolves the price of a room type for a given night, or a range of nights.
 *
 * <p>This is the single place a price is computed in this service: both the
 * reservation snapshot (taken once, at booking time) and the walk-in check-in
 * charge (resolved live, since there is no prior reservation to snapshot from)
 * go through this interface. There is one implementation today, reading from
 * {@code rate_seasons} with a fallback to {@code RoomType.basePrice} — same
 * "narrow interface, single implementation, config/data-driven variation"
 * pattern already used for {@code AlloggiatiWebSenderService}. A second
 * implementation (e.g. backed by an external revenue-management system) can be
 * added later behind this same interface without touching any caller.
 */
public interface RatePricingService {

    /**
     * Resolves the price of {@code roomTypeId} for a single {@code date}.
     *
     * @param roomTypeId the room type UUID
     * @param hotelId    the hotel UUID (multi-tenant scoping)
     * @param date       the night to price
     * @return the resolved nightly rate
     */
    NightlyRate resolveNightlyRate(UUID roomTypeId, UUID hotelId, LocalDate date);

    /**
     * Resolves the price of {@code roomTypeId} for every night of a stay.
     *
     * @param roomTypeId    the room type UUID
     * @param hotelId       the hotel UUID (multi-tenant scoping)
     * @param checkIn       the check-in date (inclusive)
     * @param checkOutExclusive the check-out date (exclusive) — same convention as
     *                          reservation booking; a stay from Mon to Wed prices
     *                          exactly 2 nights, Mon and Tue
     * @return one {@link NightlyRate} per night, in date order; empty if
     *         {@code checkOutExclusive} is not after {@code checkIn}
     */
    List<NightlyRate> resolveStayRates(UUID roomTypeId, UUID hotelId, LocalDate checkIn, LocalDate checkOutExclusive);
}
