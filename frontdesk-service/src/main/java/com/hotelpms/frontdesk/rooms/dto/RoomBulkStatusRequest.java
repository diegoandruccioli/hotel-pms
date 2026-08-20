package com.hotelpms.frontdesk.rooms.dto;

import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for bulk-updating the housekeeping status of several rooms at
 * once — backs Housekeeping's multi-select status change, replacing one
 * {@code PATCH /{id}/status} request per room.
 *
 * @param roomIds the room UUIDs to update; must not be empty
 * @param status  the new housekeeping status to apply to all of them; must not be null
 */
public record RoomBulkStatusRequest(
        @NotEmpty(message = "roomIds cannot be empty") List<UUID> roomIds,
        @NotNull(message = "Status cannot be null") RoomStatus status) {

    /**
     * Compact constructor — defensive copy of the room id list.
     */
    public RoomBulkStatusRequest {
        roomIds = roomIds == null ? List.of() : List.copyOf(roomIds);
    }

    /**
     * Returns a copy of the room id list to prevent external modification.
     *
     * @return the room UUIDs to update
     */
    @Override
    public List<UUID> roomIds() {
        return List.copyOf(roomIds);
    }
}
