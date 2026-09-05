package com.hotelpms.frontdesk.reservations.dto;

import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating reservation status and actual guest count.
 * At least one field must be meaningful; {@code status} is required.
 *
 * @param status       the new reservation status (required)
 * @param actualGuests the updated actual guests count (optional, must be positive if present)
 * @param version      the version the caller last read, for optimistic-lock conflict
 *                      detection on this partial update. Required (unlike {@link
 *                      ReservationRequest#version()}'s legacy nullable contract): this
 *                      endpoint now enforces a status transition state machine, and a
 *                      stale-write race here can silently apply an invalid transition
 *                      computed against a status the client no longer has open.
 */
public record ReservationStatusUpdateRequest(
        @NotNull(message = "Status cannot be null") ReservationStatus status,
        @Min(value = 1, message = "actualGuests must be at least 1") Integer actualGuests,
        @NotNull(message = "Version cannot be null") Long version
) {
}
