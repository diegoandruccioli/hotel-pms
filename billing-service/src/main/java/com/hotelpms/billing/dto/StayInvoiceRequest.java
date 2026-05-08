package com.hotelpms.billing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating an invoice tied to a hotel stay.
 * Called by stay-service immediately after a successful check-in.
 *
 * @param stayId        the stay UUID (logical reference — no DB FK)
 * @param guestId       the guest UUID
 * @param reservationId the reservation UUID
 */
public record StayInvoiceRequest(

        @NotNull(message = "Stay ID is required")
        UUID stayId,

        @NotNull(message = "Guest ID is required")
        UUID guestId,

        @NotNull(message = "Reservation ID is required")
        UUID reservationId) {
}
