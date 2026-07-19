package com.hotelpms.guest.dto.request;

import com.hotelpms.guest.util.ValidationConstants;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for guest creation and update operations.
 *
 * @param firstName   The first name of the guest.
 * @param lastName    The last name of the guest.
 * @param email       The email address of the guest (required if phone absent).
 * @param phone       The phone number of the guest (required if email absent).
 * @param address     The street address of the guest.
 * @param city        The city of the guest.
 * @param country     The country of the guest.
 * @param dateOfBirth  The date of birth of the guest.
 * @param fiscalCode   Italian Codice Fiscale or equivalent (optional).
 * @param vatNumber    Partita IVA / VAT number (optional).
 * @param companyName  Company / legal entity name (optional).
 * @param sdiCode      SDI/Destinatario code for electronic invoicing (optional).
 * @param pecEmail     PEC email for electronic invoicing (optional).
 * @param cap          CAP — Italian 5-digit postal code (optional; required only to
 *                     use this guest as FatturaPA cessionario).
 * @param comune       Comune — municipality name, validated together with {@code provincia}
 *                     (optional; required only to use this guest as FatturaPA cessionario).
 * @param provincia    Provincia — 2-letter province code, e.g. {@code "RM"} (optional).
 */
public record GuestRequest(
        @NotBlank
        @Size(max = ValidationConstants.MAX_FIRST_NAME_LENGTH)
        @Pattern(regexp = ValidationConstants.NAME_PATTERN)
        String firstName,
        @NotBlank
        @Size(max = ValidationConstants.MAX_LAST_NAME_LENGTH)
        @Pattern(regexp = ValidationConstants.NAME_PATTERN)
        String lastName,
        @Email @Size(max = ValidationConstants.MAX_EMAIL_LENGTH) String email,
        @Size(max = ValidationConstants.MAX_PHONE_LENGTH)
        @Pattern(regexp = ValidationConstants.PHONE_PATTERN)
        String phone,
        @Size(max = ValidationConstants.MAX_ADDRESS_LENGTH)
        @Pattern(regexp = ValidationConstants.TEXT_SAFE_PATTERN)
        String address,
        @Size(max = ValidationConstants.MAX_LOCATION_LENGTH)
        @Pattern(regexp = ValidationConstants.LOCATION_PATTERN)
        String city,
        @Size(max = ValidationConstants.MAX_LOCATION_LENGTH)
        @Pattern(regexp = ValidationConstants.LOCATION_PATTERN)
        String country,
        @Past LocalDate dateOfBirth,
        @Size(max = ValidationConstants.MAX_FISCAL_CODE_LENGTH)
        @Pattern(regexp = ValidationConstants.FISCAL_CODE_PATTERN)
        String fiscalCode,
        @Size(max = ValidationConstants.MAX_VAT_NUMBER_LENGTH)
        @Pattern(regexp = ValidationConstants.VAT_NUMBER_PATTERN)
        String vatNumber,
        @Size(max = ValidationConstants.MAX_COMPANY_NAME_LENGTH)
        @Pattern(regexp = ValidationConstants.TEXT_SAFE_PATTERN)
        String companyName,
        @Size(max = ValidationConstants.MAX_SDI_CODE_LENGTH)
        @Pattern(regexp = ValidationConstants.SDI_CODE_PATTERN)
        String sdiCode,
        @Email @Size(max = ValidationConstants.MAX_EMAIL_LENGTH)
        String pecEmail,
        @Pattern(regexp = "^$|\\d{5}", message = "CAP must be 5 digits") String cap,
        @Size(max = 100) String comune,
        @Pattern(regexp = "^$|[A-Za-z]{2}", message = "Provincia must be 2 letters") String provincia) {

    /**
     * Validates that at least one of email or phone is provided.
     *
     * @return true if email or phone is present and non-blank
     */
    @AssertTrue(message = "At least one of email or phone must be provided")
    public boolean isEmailOrPhoneProvided() {
        final boolean hasEmail = email != null && !email.isBlank();
        final boolean hasPhone = phone != null && !phone.isBlank();
        return hasEmail || hasPhone;
    }
}
