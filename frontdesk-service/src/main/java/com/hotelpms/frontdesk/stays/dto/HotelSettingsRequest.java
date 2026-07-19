package com.hotelpms.frontdesk.stays.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating hotel settings and profile.
 *
 * <p>All fields are treated as a partial patch: a {@code null} value (i.e. the field is
 * absent from the JSON body) means "leave the currently stored value unchanged" — it is
 * never interpreted as "clear this field". This lets callers such as a single settings
 * toggle send only the field they changed without wiping out the rest of the hotel
 * profile. Boolean flags therefore use the boxed {@link Boolean} type rather than the
 * primitive {@code boolean}, since Jackson cannot otherwise distinguish "absent" from
 * "explicitly false".
 *
 * @param alloggiatiAutoSend             whether to automatically submit the Alloggiati
 *                                        report at check-in ({@code null} = unchanged)
 * @param hotelName                      display name of the hotel property (optional)
 * @param address                        full street address including civic number (optional)
 * @param vatNumber                      Partita IVA — Italian VAT number (optional)
 * @param fiscalCode                     Codice Fiscale — Italian fiscal code (optional)
 * @param logoUrl                        URL of the hotel logo image (optional)
 * @param alloggiatiUsername             Alloggiati Web portal username for this hotel (optional —
 *                                        falls back to the global instance credentials when absent)
 * @param alloggiatiPassword             Alloggiati Web portal password, write-only: blank/null means
 *                                        "leave the currently stored password unchanged", it is never
 *                                        sent back by the API to be cleared by omission
 * @param alloggiatiWsKey                Alloggiati Web WsKey, same write-only semantics as the password
 * @param sendReservationConfirmedEmail  whether to email the guest when a reservation is created
 *                                        ({@code null} = unchanged)
 * @param sendCheckoutEmail              whether to email the guest a stay summary at check-out
 *                                        ({@code null} = unchanged)
 * @param emailSubjectReservationConfirmed optional custom subject line for the reservation-confirmed
 *                                        email; blank/null falls back to the default IT/EN subject
 * @param emailSubjectCheckout           optional custom subject line for the checkout email;
 *                                        blank/null falls back to the default IT/EN subject
 * @param emailGreetingText              optional greeting/signature line appended to every
 *                                        transactional email footer (optional)
 * @param cap                            CAP — Italian 5-digit postal code (optional; required
 *                                        only to export a valid FatturaPA XML)
 * @param comune                         Comune — municipality name, validated together with
 *                                        {@code provincia} against the Alloggiati Web reference
 *                                        data (optional; required only to export FatturaPA)
 * @param provincia                      Provincia — 2-letter province code, e.g. {@code "RM"}
 *                                        (optional; required only to export FatturaPA)
 */
public record HotelSettingsRequest(
        Boolean alloggiatiAutoSend,
        String hotelName,
        String address,
        String vatNumber,
        String fiscalCode,
        String logoUrl,
        @Size(max = 100) String alloggiatiUsername,
        @Size(max = MAX_CREDENTIAL_LENGTH) String alloggiatiPassword,
        @Size(max = MAX_CREDENTIAL_LENGTH) String alloggiatiWsKey,
        Boolean sendReservationConfirmedEmail,
        Boolean sendCheckoutEmail,
        @Size(max = MAX_SUBJECT_LENGTH) String emailSubjectReservationConfirmed,
        @Size(max = MAX_SUBJECT_LENGTH) String emailSubjectCheckout,
        @Size(max = MAX_GREETING_LENGTH) String emailGreetingText,
        @Pattern(regexp = "^$|\\d{5}", message = "CAP must be 5 digits") String cap,
        @Size(max = 100) String comune,
        @Pattern(regexp = "^$|[A-Za-z]{2}", message = "Provincia must be 2 letters") String provincia) {

    /** Maximum length accepted for the Alloggiati Web password/WsKey fields. */
    public static final int MAX_CREDENTIAL_LENGTH = 200;

    /** Maximum length accepted for the custom email subject fields. */
    public static final int MAX_SUBJECT_LENGTH = 200;

    /** Maximum length accepted for the email greeting/signature field. */
    public static final int MAX_GREETING_LENGTH = 300;
}
