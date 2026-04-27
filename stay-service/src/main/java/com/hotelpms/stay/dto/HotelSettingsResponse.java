package com.hotelpms.stay.dto;

import java.util.UUID;

/**
 * Response DTO for hotel settings.
 *
 * @param hotelId            the hotel identifier
 * @param alloggiatiAutoSend whether automatic Alloggiati submission is enabled
 */
public record HotelSettingsResponse(UUID hotelId, boolean alloggiatiAutoSend) {
}
