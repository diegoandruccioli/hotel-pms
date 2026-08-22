package com.hotelpms.gateway.events;

import java.time.Instant;

/**
 * A realtime notification pushed to browser clients over the SSE stream
 * (T-GW-09). Deliberately thin: the frontend does not apply an optimistic
 * patch from {@code type} — it invalidates the relevant React Query cache
 * entries and lets a normal fetch pick up the new state, so this payload
 * only needs to say what kind of change happened and when.
 *
 * @param type      the kind of change that occurred
 * @param timestamp when the change was published, server time
 */
public record RoomEvent(RoomEventType type, Instant timestamp) {

    /**
     * Creates an event of the given type, stamped with the current time.
     *
     * @param type the kind of change that occurred
     * @return a new event
     */
    public static RoomEvent of(final RoomEventType type) {
        return new RoomEvent(type, Instant.now());
    }
}
