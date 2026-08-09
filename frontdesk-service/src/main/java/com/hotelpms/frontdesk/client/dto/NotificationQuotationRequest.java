package com.hotelpms.frontdesk.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body sent to {@code POST /internal/notifications/quotation}.
 *
 * @param guestEmail     recipient address (guest or prospect)
 * @param guestName      display name for personalisation
 * @param hotelName      hotel display name
 * @param checkInDate    the quoted stay's check-in date
 * @param checkOutDate   the quoted stay's check-out date
 * @param expectedGuests expected number of guests, if known
 * @param totalAmount    total quoted price
 * @param currency       ISO 4217 currency code (e.g. EUR)
 * @param validUntil     the last date the quoted price is honored
 * @param locale         BCP 47 locale tag (e.g. "it", "en")
 * @param greetingText   optional per-hotel greeting/signature line for the email footer
 * @param logoUrl        optional per-hotel logo image URL rendered in the email header
 * @param quotationPdf   the quotation PDF bytes to attach
 * @param quotationFileName the attachment file name
 */
@SuppressWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2" })
public record NotificationQuotationRequest(
        String guestEmail,
        String guestName,
        String hotelName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer expectedGuests,
        BigDecimal totalAmount,
        String currency,
        LocalDate validUntil,
        String locale,
        String greetingText,
        String logoUrl,
        byte[] quotationPdf,
        String quotationFileName) {

    /**
     * Compact constructor — defensive copy of the PDF bytes.
     */
    public NotificationQuotationRequest {
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
