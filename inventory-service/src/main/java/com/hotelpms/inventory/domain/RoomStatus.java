package com.hotelpms.inventory.domain;

/**
 * Represents the housekeeping status of a hotel room.
 */
public enum RoomStatus {

    /**
     * The room has been cleaned and is ready for guests.
     */
    CLEAN,

    /**
     * The room requires cleaning (e.g., after a check-out).
     */
    DIRTY,

    /**
     * The room is undergoing maintenance and is not available.
     */
    MAINTENANCE
}
