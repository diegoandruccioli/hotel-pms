package com.hotelpms.fb.client.dto;

import java.util.UUID;

/**
 * Lightweight local record representation of a Stay response.
 * Used to decode HTTP responses when checking if a guest is currently checked
 * into the hotel.
 *
 * @param id     the ID of the stay
 * @param status the status of the stay (e.g., CHECKED_IN)
 */
public record StayResponse(
        UUID id,
        String status) {
}
