package com.hotelpms.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for ReservationLineItem.
 *
 * @param id        the id
 * @param roomId    the room id
 * @param price     the price
 * @param active    is active
 * @param createdAt creation time
 * @param updatedAt update time
 */
public record ReservationLineItemResponse(
        UUID id,
        UUID roomId,
        BigDecimal price,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
