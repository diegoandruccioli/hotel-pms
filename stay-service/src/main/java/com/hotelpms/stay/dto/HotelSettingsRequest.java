package com.hotelpms.stay.dto;

/**
 * Request body for updating hotel settings and profile.
 *
 * @param alloggiatiAutoSend whether to automatically submit the Alloggiati report at check-in
 * @param hotelName          display name of the hotel property (optional)
 * @param address            full street address including civic number (optional)
 * @param vatNumber          Partita IVA — Italian VAT number (optional)
 * @param fiscalCode         Codice Fiscale — Italian fiscal code (optional)
 * @param logoUrl            URL of the hotel logo image (optional)
 */
public record HotelSettingsRequest(
        boolean alloggiatiAutoSend,
        String hotelName,
        String address,
        String vatNumber,
        String fiscalCode,
        String logoUrl) {
}
