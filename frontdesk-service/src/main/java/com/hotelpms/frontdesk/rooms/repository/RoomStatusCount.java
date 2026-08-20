package com.hotelpms.frontdesk.rooms.repository;

import com.hotelpms.frontdesk.rooms.domain.RoomStatus;

/**
 * Spring Data interface projection for a {@code GROUP BY status} count over
 * a hotel's rooms. Backs the day-sheet room-status breakdown — one query
 * instead of one {@code COUNT} per {@link RoomStatus} value.
 */
public interface RoomStatusCount {

    /**
     * The housekeeping status this count is for.
     *
     * @return the room status
     */
    RoomStatus getStatus();

    /**
     * The number of active rooms with this status, for the queried hotel.
     *
     * @return the count
     */
    long getCount();
}
