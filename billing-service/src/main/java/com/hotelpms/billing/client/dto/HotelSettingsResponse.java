package com.hotelpms.billing.client.dto;

import java.util.UUID;

/**
 * External DTO for hotel profile data fetched from stay-service.
 *
 * @param hotelId    the hotel identifier
 * @param hotelName  display name of the hotel property
 * @param address    full street address
 * @param vatNumber  Partita IVA — Italian VAT number
 * @param fiscalCode Codice Fiscale — Italian fiscal code
 * @param logoUrl    optional URL of the hotel logo image
 */
public record HotelSettingsResponse(
        UUID hotelId,
        String hotelName,
        String address,
        String vatNumber,
        String fiscalCode,
        String logoUrl) {
}
