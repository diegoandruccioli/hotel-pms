package com.hotelpms.frontdesk.stays.repository;

import java.util.UUID;

/**
 * Spring Data interface projection for a {@code GROUP BY stay} guest count.
 * Backs the housekeeping worksheet's pax column — see {@link
 * StayRepository#countGuestsByStayForHotelIdAndStatus} for why this stays a
 * count-only aggregate and never fetches {@code StayGuest} entities.
 */
public interface StayGuestCount {

    /**
     * The stay this count is for.
     *
     * @return the stay UUID
     */
    UUID getStayId();

    /**
     * The number of guests attached to this stay.
     *
     * @return the count
     */
    long getGuestCount();
}
