package com.hotelpms.stay.client.dto;

import java.util.UUID;

/**
 * Data Transfer Object representing a Room from the Inventory Service.
 *
 * @param id         the room ID
 * @param roomNumber the room number
 * @param status     the room status
 */
public record RoomResponse(
        UUID id,
        String roomNumber,
        String status) {
}
