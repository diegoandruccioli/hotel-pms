package com.hotelpms.guest.dto.response;

import java.util.UUID;

/**
 * Response DTO for per-hotel GDPR retention settings (T-GST-05).
 *
 * @param hotelId             the hotel UUID
 * @param guestRetentionYears the configured retention period in years
 * @param tulpsMinYears       the TULPS legal minimum (informational, always 5)
 * @param fiscalMinYears      the fiscal legal minimum (informational, always 10)
 */
public record GuestPrivacySettingsResponse(
        UUID hotelId,
        int guestRetentionYears,
        int tulpsMinYears,
        int fiscalMinYears) {
}
