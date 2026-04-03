package com.hotelpms.reservation.client.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for RoomType from Inventory Service.
 *
 * @param id          the id
 * @param code        the code
 * @param description the description
 * @param active      is active
 * @param createdAt   creation time
 * @param updatedAt   update time
 */
public record RoomTypeResponse(
        UUID id,
        String code,
        String description,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
