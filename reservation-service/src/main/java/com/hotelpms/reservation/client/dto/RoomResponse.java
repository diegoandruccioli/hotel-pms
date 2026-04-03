package com.hotelpms.reservation.client.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for Room from Inventory Service.
 *
 * @param id         the id
 * @param roomNumber the room number
 * @param roomType   the room type
 * @param status     the status
 * @param active     is active
 * @param createdAt  creation time
 * @param updatedAt  update time
 */
public record RoomResponse(
        UUID id,
        String roomNumber,
        RoomTypeResponse roomType,
        String status,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
