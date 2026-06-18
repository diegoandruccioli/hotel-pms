package com.hotelpms.frontdesk.rooms.domain;

/**
 * Represents the operational status of a hotel room.
 *
 * <p>CLEAN / DIRTY / MAINTENANCE are housekeeping states managed by cleaning staff.
 * OCCUPIED is set by the stays Saga (check-in) and cleared to DIRTY at check-out.
 */
public enum RoomStatus {

    /**
     * The room has been cleaned and is ready for new guests.
     */
    CLEAN,

    /**
     * The room requires cleaning (e.g., after a check-out).
     */
    DIRTY,

    /**
     * The room is undergoing maintenance and is not available for booking.
     */
    MAINTENANCE,

    /**
     * A guest is currently checked in — set by the check-in Saga, cleared to DIRTY at check-out.
     * Housekeeping staff cannot change this status directly.
     */
    OCCUPIED
}
