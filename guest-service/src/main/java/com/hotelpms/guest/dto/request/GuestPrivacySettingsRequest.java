package com.hotelpms.guest.dto.request;

import com.hotelpms.guest.model.GuestPrivacySettings;
import jakarta.validation.constraints.Min;

/**
 * Request DTO for updating per-hotel GDPR retention settings (T-GST-05).
 *
 * @param guestRetentionYears the hotel's preferred minimum retention period in
 *                            years from the guest's last stay; must be at least
 *                            {@value GuestPrivacySettings#TULPS_MIN_YEARS} (TULPS
 *                            legal minimum)
 */
public record GuestPrivacySettingsRequest(
        @Min(value = GuestPrivacySettings.TULPS_MIN_YEARS,
                message = "guestRetentionYears must be at least "
                        + GuestPrivacySettings.TULPS_MIN_YEARS
                        + " (TULPS legal minimum)")
        int guestRetentionYears) {
}
