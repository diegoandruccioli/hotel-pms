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
 * @param sendReservationConfirmedEmail   whether the guest is emailed when a reservation is created
 * @param sendCheckoutEmail               whether the guest is emailed a stay summary at check-out
 * @param emailSubjectReservationConfirmed custom subject line for the reservation-confirmed
 *                                        email, or {@code null} if using the default
 * @param emailSubjectCheckout            custom subject line for the checkout email,
 *                                        or {@code null} if using the default
 * @param emailGreetingText               greeting/signature line appended to every
 *                                        transactional email footer, or {@code null} if unset
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
        boolean alloggiatiCredentialsConfigured,
        boolean sendReservationConfirmedEmail,
        boolean sendCheckoutEmail,
        String emailSubjectReservationConfirmed,
        String emailSubjectCheckout,
        String emailGreetingText) {
}
