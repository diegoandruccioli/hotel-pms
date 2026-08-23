package com.hotelpms.frontdesk.stays.repository;

import java.time.LocalDate;

/**
 * Spring Data interface projection for one time-bucket row of the occupied
 * room-nights aggregate. Backs the occupancy trend report — a single grouped
 * native query instead of loading every stay and summing in Java.
 */
public interface StayOccupancyPeriod {

    /**
     * The start of this time bucket.
     *
     * @return the bucket start date
     */
    LocalDate getPeriodStart();

    /**
     * Nights actually stayed, summed across every {@code CHECKED_OUT} stay
     * whose arrival falls in this bucket.
     *
     * @return the occupied room-nights for this bucket
     */
    long getOccupiedRoomNights();
}
