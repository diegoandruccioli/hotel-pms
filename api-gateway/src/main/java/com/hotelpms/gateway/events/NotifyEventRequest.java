package com.hotelpms.gateway.events;

/**
 * Body of {@code POST /internal/events/notify} — the internal call
 * frontdesk-service makes after a room-status or stay-status mutation
 * commits.
 *
 * @param type the kind of change that occurred
 */
public record NotifyEventRequest(RoomEventType type) {
}
