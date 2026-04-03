package com.hotelpms.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for ReservationLineItem.
 *
 * @param roomId the room id
 * @param price  the price
 */
public record ReservationLineItemRequest(
        @NotNull(message = "Room ID is mandatory") UUID roomId,

        @NotNull(message = "Price is mandatory") @Positive(message = "Price must be positive") BigDecimal price) {
}
