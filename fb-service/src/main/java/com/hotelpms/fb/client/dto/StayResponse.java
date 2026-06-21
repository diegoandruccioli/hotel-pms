package com.hotelpms.fb.client.dto;

import java.util.UUID;

/**
 * Lightweight local record representation of a Stay response.
 * Used to decode HTTP responses when checking if a guest is currently checked
 * into the hotel.
 *
 * @param id         the ID of the stay
 * @param status     the status of the stay (e.g., CHECKED_IN)
 * @param roomNumber the denormalized room number, copied onto the order at
 *                   creation time so it survives later room/guest changes
 */
public record StayResponse(
        UUID id,
        String status,
        String roomNumber) {
}
