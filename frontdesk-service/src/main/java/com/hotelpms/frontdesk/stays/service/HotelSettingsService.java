package com.hotelpms.frontdesk.stays.service;

import com.hotelpms.frontdesk.stays.dto.HotelSettingsRequest;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;

import java.util.UUID;

/**
 * Service for managing per-hotel operational settings.
 */
public interface HotelSettingsService {

    /**
     * Returns settings for the given hotel, creating a default row if none exists.
     *
     * @param hotelId the hotel identifier
     * @return the current settings
     */
    HotelSettingsResponse getOrCreate(UUID hotelId);

    /**
     * Updates settings for the given hotel, creating the row if it does not yet exist.
     *
     * @param hotelId the hotel identifier
     * @param request the new settings values
     * @return the updated settings
     */
    HotelSettingsResponse update(UUID hotelId, HotelSettingsRequest request);
}
