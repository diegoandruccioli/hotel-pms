package com.hotelpms.inventory.dto;

import java.io.Serializable;
import com.hotelpms.inventory.domain.RoomStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for Room.
 *
 * @param id         the id
 * @param hotelId    the hotel id
 * @param roomNumber the room number
 * @param roomType   the room type
 * @param status     the room status
 * @param active     is active
 * @param createdAt  creation time
 * @param updatedAt  update time
 */
public record RoomResponse(
        UUID id,
        UUID hotelId,
        String roomNumber,
        RoomTypeResponse roomType,
        RoomStatus status,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {
}
