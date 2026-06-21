package com.hotelpms.frontdesk.stays.dto;

import java.util.UUID;

/**
 * Response DTO for hotel settings and profile.
 *
 * <p>The Alloggiati password and WsKey are write-only and intentionally absent
 * here — only the non-secret username and a boolean summarizing whether
 * hotel-specific credentials are configured are ever returned.
 *
 * @param hotelId                         the hotel identifier
 * @param alloggiatiAutoSend              whether automatic Alloggiati submission is enabled
 * @param hotelName                       display name of the hotel property
 * @param address                         full street address including civic number
 * @param vatNumber                       Partita IVA — Italian VAT number
 * @param fiscalCode                      Codice Fiscale — Italian fiscal code
 * @param logoUrl                         URL of the hotel logo image
 * @param alloggiatiUsername              Alloggiati Web portal username for this hotel, if configured
 * @param alloggiatiCredentialsConfigured whether this hotel has its own Alloggiati Web
 *                                        credentials (username + password + WsKey all set),
 *                                        as opposed to falling back to the global ones
 */
public record HotelSettingsResponse(
        UUID hotelId,
        boolean alloggiatiAutoSend,
        String hotelName,
        String address,
        String vatNumber,
        String fiscalCode,
        String logoUrl,
        String alloggiatiUsername,
        boolean alloggiatiCredentialsConfigured) {
}
