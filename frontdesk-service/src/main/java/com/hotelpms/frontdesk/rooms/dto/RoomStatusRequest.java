package com.hotelpms.frontdesk.rooms.dto;

import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating a room's housekeeping status.
 * Wraps the enum so that {@code @Valid} + {@code @NotNull} can enforce
 * that the field is always present, rejecting requests with a missing body.
 *
 * @param status the new room status; must not be null
 */
public record RoomStatusRequest(
        @NotNull(message = "Status cannot be null") RoomStatus status) {
}
