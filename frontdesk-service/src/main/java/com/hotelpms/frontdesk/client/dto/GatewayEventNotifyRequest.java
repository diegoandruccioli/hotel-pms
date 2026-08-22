package com.hotelpms.frontdesk.client.dto;

/**
 * Body of the internal {@code POST /internal/events/notify} call to
 * api-gateway (T-GW-09b).
 *
 * @param type the kind of change that occurred
 */
public record GatewayEventNotifyRequest(GatewayEventType type) {

    /**
     * Mirrors api-gateway's own {@code RoomEventType} enum by name — the two
     * modules have no shared DTO layer for internal notify contracts (same as
     * every other {@code Notification*} request in this package), so this is
     * a deliberate, small duplication. JSON wire format for both sides is the
     * enum constant name, so the two stay in sync as long as the names match.
     */
    public enum GatewayEventType {
        /** A room's housekeeping status changed (single or bulk update). */
        ROOM_STATUS_CHANGED,
        /** A stay checked in — the room it occupies moved to OCCUPIED. */
        CHECK_IN,
        /** A stay checked out — the room it occupied moved to DIRTY. */
        CHECK_OUT
    }
}
