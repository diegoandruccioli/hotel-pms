package com.hotelpms.frontdesk.reservations.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for ReservationLineItem.
 *
 * <p>Deliberately has no {@code price} field: the price is always resolved
 * server-side by {@code RatePricingService} and snapshotted onto the line item
 * at creation/update time — a client-supplied price was never validated
 * against anything and was silently ignored downstream (see {@code
 * ReservationServiceImpl}), which is exactly the bug this closes.
 *
 * @param roomId the room id
 */
public record ReservationLineItemRequest(
        @NotNull(message = "Room ID is mandatory") UUID roomId) {
}
