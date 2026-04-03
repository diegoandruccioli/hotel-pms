package com.hotelpms.reservation.dto;

import com.hotelpms.reservation.domain.ReservationStatus;

/**
 * DTO for updating reservation status and actual guest count.
 *
 * @param status       the new reservation status (optional)
 * @param actualGuests the updated actual guests count (optional)
 */
public record ReservationStatusUpdateRequest(
        ReservationStatus status,
        Integer actualGuests
) {
}

