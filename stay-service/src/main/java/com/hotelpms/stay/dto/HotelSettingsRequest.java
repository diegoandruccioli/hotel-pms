package com.hotelpms.stay.dto;

/**
 * Request body for updating hotel settings.
 *
 * @param alloggiatiAutoSend whether to automatically submit the Alloggiati report at check-in
 */
public record HotelSettingsRequest(boolean alloggiatiAutoSend) {
}
