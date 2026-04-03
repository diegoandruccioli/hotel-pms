package com.hotelpms.stay.client.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Client DTO representing a Reservation Line Item from the Reservation Service.
 *
 * @param id        the id
 * @param roomId    the room id
 * @param active    is active
 * @param createdAt creation time
 * @param updatedAt update time
 */
public record ReservationLineItemResponse(
        UUID id,
        UUID roomId,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
