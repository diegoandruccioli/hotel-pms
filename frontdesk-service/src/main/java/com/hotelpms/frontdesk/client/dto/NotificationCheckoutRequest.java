package com.hotelpms.frontdesk.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request body sent to {@code POST /internal/notifications/checkout}.
 *
 * @param guestEmail     recipient address
 * @param guestName      guest full name for personalisation
 * @param hotelName      hotel display name
 * @param roomNumber     room number
 * @param actualCheckIn  actual check-in timestamp
 * @param actualCheckOut actual check-out timestamp
 * @param lines          ordered invoice charge lines (may be null/empty)
 * @param totalAmount    total billed amount
 * @param currency       ISO 4217 currency code (e.g. EUR)
 * @param locale         BCP 47 locale tag (e.g. "it", "en")
 * @param customSubject  optional per-hotel subject override; blank/null uses the default
 * @param greetingText   optional per-hotel greeting/signature line for the email footer
 * @param logoUrl        optional per-hotel logo image URL rendered in the email header
 */
public record NotificationCheckoutRequest(
        String guestEmail,
        String guestName,
        String hotelName,
        String roomNumber,
        LocalDateTime actualCheckIn,
        LocalDateTime actualCheckOut,
        List<NotificationChargeLineDto> lines,
        BigDecimal totalAmount,
        String currency,
        String locale,
        String customSubject,
        String greetingText,
        String logoUrl) {

    /**
     * Compact constructor to ensure defensive copying of the lines list.
     */
    public NotificationCheckoutRequest {
        lines = lines == null ? null : List.copyOf(lines);
    }

    /**
     * Returns a copy of the lines list to prevent external modification.
     *
     * @return the invoice charge lines
     */
    @Override
    public List<NotificationChargeLineDto> lines() {
        return lines == null ? null : List.copyOf(lines);
    }
}
