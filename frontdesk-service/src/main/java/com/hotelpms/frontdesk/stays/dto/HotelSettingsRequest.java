package com.hotelpms.frontdesk.stays.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for updating hotel settings and profile.
 *
 * @param alloggiatiAutoSend whether to automatically submit the Alloggiati report at check-in
 * @param hotelName          display name of the hotel property (optional)
 * @param address            full street address including civic number (optional)
 * @param vatNumber          Partita IVA — Italian VAT number (optional)
 * @param fiscalCode         Codice Fiscale — Italian fiscal code (optional)
 * @param logoUrl            URL of the hotel logo image (optional)
 * @param alloggiatiUsername Alloggiati Web portal username for this hotel (optional —
 *                            falls back to the global instance credentials when absent)
 * @param alloggiatiPassword Alloggiati Web portal password, write-only: blank/null means
 *                            "leave the currently stored password unchanged", it is never
 *                            sent back by the API to be cleared by omission
 * @param alloggiatiWsKey    Alloggiati Web WsKey, same write-only semantics as the password
 */
public record HotelSettingsRequest(
        boolean alloggiatiAutoSend,
        String hotelName,
        String address,
        String vatNumber,
        String fiscalCode,
        String logoUrl,
        @Size(max = 100) String alloggiatiUsername,
        @Size(max = MAX_CREDENTIAL_LENGTH) String alloggiatiPassword,
        @Size(max = MAX_CREDENTIAL_LENGTH) String alloggiatiWsKey) {

    /** Maximum length accepted for the Alloggiati Web password/WsKey fields. */
    public static final int MAX_CREDENTIAL_LENGTH = 200;
}
