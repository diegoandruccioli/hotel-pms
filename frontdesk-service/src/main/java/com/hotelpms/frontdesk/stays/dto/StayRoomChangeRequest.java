package com.hotelpms.frontdesk.stays.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Data Transfer Object for moving an open stay to a different room (Parte 6).
 *
 * @param newRoomId the destination room id
 * @param version   the version the client last read, for optimistic-lock
 *                  conflict detection; {@code null} skips the check
 *                  (backward-compatible for callers that don't send it yet)
 */
public record StayRoomChangeRequest(
        @NotNull(message = "New room is required") UUID newRoomId,
        Long version
) {
}
