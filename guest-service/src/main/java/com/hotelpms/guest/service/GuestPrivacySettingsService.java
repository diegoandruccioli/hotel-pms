package com.hotelpms.guest.service;

import com.hotelpms.guest.dto.request.GuestPrivacySettingsRequest;
import com.hotelpms.guest.dto.response.GuestPrivacySettingsResponse;
import com.hotelpms.guest.model.GuestPrivacySettings;

import java.util.UUID;

/**
 * Service for managing per-hotel GDPR data-retention settings (T-GST-05).
 */
public interface GuestPrivacySettingsService {

    /**
     * Returns the GDPR settings for a hotel, creating a default row if none exists.
     *
     * @param hotelId the hotel UUID
     * @return the current settings response
     */
    GuestPrivacySettingsResponse getOrCreate(UUID hotelId);

    /**
     * Updates the retention period for a hotel.
     *
     * @param hotelId the hotel UUID
     * @param request the new settings
     * @return the updated settings response
     */
    GuestPrivacySettingsResponse update(UUID hotelId, GuestPrivacySettingsRequest request);

    /**
     * Returns the raw settings entity for a hotel (used by the legal-hold guard
     * and retention job without going through the response DTO mapping).
     *
     * @param hotelId the hotel UUID
     * @return the entity, creating a default if none exists
     */
    GuestPrivacySettings getOrCreateEntity(UUID hotelId);
}
