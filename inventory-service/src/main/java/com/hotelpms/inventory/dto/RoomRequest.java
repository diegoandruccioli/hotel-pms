package com.hotelpms.inventory.dto;

import com.hotelpms.inventory.domain.RoomStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for Room.
 *
 * @param hotelId    the hotel id for multi-tenancy
 * @param roomNumber the room number
 * @param roomTypeId the room type id
 * @param status     the room status
 */
public record RoomRequest(
        UUID hotelId,

        @NotBlank(message = "Room number cannot be blank") String roomNumber,

        @NotNull(message = "Room Type ID cannot be null") UUID roomTypeId,

        @NotNull(message = "Status cannot be null") RoomStatus status) {
}
