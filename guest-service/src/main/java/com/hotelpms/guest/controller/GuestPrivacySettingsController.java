package com.hotelpms.guest.controller;

import com.hotelpms.guest.dto.request.GuestPrivacySettingsRequest;
import com.hotelpms.guest.dto.response.GuestPrivacySettingsResponse;
import com.hotelpms.guest.service.GuestPrivacySettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller exposing per-hotel GDPR retention settings under
 * {@code /api/v1/guests/settings}.
 */
@RestController
@RequestMapping("/api/v1/guests/settings")
@RequiredArgsConstructor
public final class GuestPrivacySettingsController {

    private final GuestPrivacySettingsService service;

    /**
     * Returns the current GDPR retention settings for the caller's hotel.
     * Creates a default row (5 years) if none exists yet.
     *
     * @return 200 OK with the settings response
     */
    @GetMapping
    public ResponseEntity<GuestPrivacySettingsResponse> getSettings() {
        return ResponseEntity.ok(service.getOrCreate(extractHotelId()));
    }

    /**
     * Updates the GDPR retention period for the caller's hotel.
     * The value must be {@code >= 5} (TULPS legal minimum).
     *
     * @param request the new settings; must be valid and non-null
     * @return 200 OK with the updated settings response
     */
    @PutMapping
    public ResponseEntity<GuestPrivacySettingsResponse> updateSettings(
            @NonNull @Valid @RequestBody final GuestPrivacySettingsRequest request) {
        return ResponseEntity.ok(service.update(extractHotelId(), request));
    }

    private UUID extractHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr)
                || hotelIdStr.isBlank()) {
            throw new IllegalStateException("HOTEL_ID_NOT_AVAILABLE");
        }
        return UUID.fromString(hotelIdStr);
    }
}
