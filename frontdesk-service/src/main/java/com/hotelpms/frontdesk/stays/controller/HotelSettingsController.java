package com.hotelpms.frontdesk.stays.controller;

import com.hotelpms.internalauth.security.TenantContext;

import com.hotelpms.frontdesk.stays.dto.HotelSettingsRequest;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * REST controller for per-hotel operational settings.
 * Mounted under {@code /api/v1/stays/settings} — protected by the gateway JWT filter.
 */
@RestController
@RequestMapping("/api/v1/stays/settings")
@RequiredArgsConstructor
public class HotelSettingsController {

    private final HotelSettingsService hotelSettingsService;

    /**
     * Returns settings for the authenticated hotel, creating defaults if none exist.
     *
     * @return the hotel settings
     */
    @GetMapping
    public HotelSettingsResponse getSettings() {
        return hotelSettingsService.getOrCreate(TenantContext.resolveHotelId());
    }

    /**
     * Updates settings for the authenticated hotel.
     * Restricted to ADMIN and OWNER — RECEPTIONIST must not be able to modify
     * hotel-level configuration even via direct API calls.
     *
     * @param request the new settings values
     * @return the updated settings
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @PutMapping
    public HotelSettingsResponse updateSettings(@Valid @RequestBody final HotelSettingsRequest request) {
        return hotelSettingsService.update(TenantContext.resolveHotelId(), request);
    }

}
