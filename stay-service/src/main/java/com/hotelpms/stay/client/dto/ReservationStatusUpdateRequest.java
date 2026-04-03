package com.hotelpms.stay.client.dto;

/**
 * Client DTO for updating reservation status and guests via Reservation Service.
 *
 * @param status       the new reservation status (string enum name, optional)
 * @param actualGuests the updated actual guests count (optional)
 */
public record ReservationStatusUpdateRequest(
        String status,
        Integer actualGuests
) {
}

