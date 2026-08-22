package com.hotelpms.gateway.events;

/**
 * The kinds of realtime change frontdesk-service notifies the gateway about.
 * The frontend maps each to which React Query cache entries to invalidate.
 */
public enum RoomEventType {

    /** A room's housekeeping status changed (single or bulk update). */
    ROOM_STATUS_CHANGED,

    /** A stay checked in — the room it occupies moved to OCCUPIED. */
    CHECK_IN,

    /** A stay checked out — the room it occupied moved to DIRTY. */
    CHECK_OUT
}
