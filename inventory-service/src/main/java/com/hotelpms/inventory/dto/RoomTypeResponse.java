package com.hotelpms.inventory.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for Room Type.
 *
 * @param id           the id
 * @param name         the name
 * @param description  the description
 * @param maxOccupancy the maximum occupancy
 * @param basePrice    the base price
 * @param active       is active
 * @param createdAt    creation time
 * @param updatedAt    update time
 */
public record RoomTypeResponse(
                UUID id,
                String name,
                String description,
                Integer maxOccupancy,
                BigDecimal basePrice,
                boolean active,
                LocalDateTime createdAt,
                LocalDateTime updatedAt) implements Serializable {
}
