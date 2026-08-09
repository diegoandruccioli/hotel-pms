package com.hotelpms.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for a quotation email with the priced offer PDF attached.
 *
 * @param guestEmail     recipient address (guest or prospect)
 * @param guestName      display name for personalisation
 * @param hotelName      hotel display name
 * @param checkInDate    the quoted stay's check-in date
 * @param checkOutDate   the quoted stay's check-out date
 * @param expectedGuests expected number of guests, if known
 * @param totalAmount    the lowest option's total price — a "starting from" price
 *                       when {@code optionsCount > 1}, the flat total otherwise
 * @param optionsCount   how many alternative options this quotation offers (1-5);
 *                       full per-option detail lives only in the attached PDF
 * @param currency       ISO 4217 currency code (e.g. "EUR")
 * @param validUntil     the last date the quoted price is honored
 * @param locale         BCP-47 language tag used to select the template ("it" or "en")
 * @param greetingText   optional per-hotel greeting/signature line for the email footer
 * @param logoUrl        optional per-hotel logo image URL rendered in the email header
 * @param quotationPdf   the quotation PDF bytes to attach; required — a quotation
 *                       email without the priced offer is not useful to the guest
 * @param quotationFileName the attachment file name
 */
@SuppressWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
public record QuotationNotificationRequest(
        @NotBlank @Email String guestEmail,
        @NotBlank String guestName,
        String hotelName,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        Integer expectedGuests,
        @NotNull BigDecimal totalAmount,
        int optionsCount,
        String currency,
        @NotNull LocalDate validUntil,
        String locale,
        String greetingText,
        String logoUrl,
        @NotNull byte[] quotationPdf,
        @NotBlank String quotationFileName) {

    /**
     * Compact constructor — defensive copy of the PDF bytes.
     */
    public QuotationNotificationRequest {
        quotationPdf = quotationPdf == null ? null : quotationPdf.clone();
    }

    /**
     * Returns a defensive copy of the quotation PDF bytes.
     *
     * @return the PDF bytes
     */
    @Override
    public byte[] quotationPdf() {
        return quotationPdf == null ? null : quotationPdf.clone();
    }
}
