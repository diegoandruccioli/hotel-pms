package com.hotelpms.frontdesk.stays.dto;

import java.util.UUID;

/**
 * Response DTO for hotel settings and profile.
 *
 * @param hotelId            the hotel identifier
 * @param alloggiatiAutoSend whether automatic Alloggiati submission is enabled
 * @param hotelName          display name of the hotel property
 * @param address            full street address including civic number
 * @param vatNumber          Partita IVA — Italian VAT number
 * @param fiscalCode         Codice Fiscale — Italian fiscal code
 * @param logoUrl            URL of the hotel logo image
 */
public record HotelSettingsResponse(
        UUID hotelId,
        boolean alloggiatiAutoSend,
        String hotelName,
        String address,
        String vatNumber,
        String fiscalCode,
        String logoUrl) {
}
