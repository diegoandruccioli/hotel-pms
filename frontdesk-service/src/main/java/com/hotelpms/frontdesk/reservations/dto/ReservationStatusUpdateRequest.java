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
 *                      detection on this partial update; {@code null} skips the check
 *                      (backward-compatible for callers that don't send it yet — same
 *                      contract as {@link ReservationRequest#version()})
 */
public record ReservationStatusUpdateRequest(
        @NotNull(message = "Status cannot be null") ReservationStatus status,
        @Min(value = 1, message = "actualGuests must be at least 1") Integer actualGuests,
        Long version
) {
}
