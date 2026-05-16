package com.hotelpms.stay.client.dto;

/**
 * Request DTO sent to inventory-service to update room housekeeping status.
 *
 * @param status the new room status string (e.g. "OCCUPIED", "DIRTY", "CLEAN")
 */
public record RoomStatusRequest(String status) {
}
