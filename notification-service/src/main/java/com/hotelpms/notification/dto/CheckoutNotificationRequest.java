package com.hotelpms.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload for a check-out notification including invoice summary.
 *
 * @param guestEmail      the guest's email address
 * @param guestName       full name of the guest
 * @param hotelName       display name of the hotel property
 * @param roomNumber      the room number that was occupied
 * @param actualCheckIn   the actual check-in date-time
 * @param actualCheckOut  the actual check-out date-time
 * @param lines           invoice charge lines (may be empty if billing-service was unavailable)
 * @param totalAmount     total invoice amount
 * @param currency        ISO 4217 currency code (e.g. "EUR")
 * @param locale          BCP-47 language tag used to select the template ("it" or "en")
 * @param customSubject   optional per-hotel subject override; blank/null uses the default
 * @param greetingText    optional per-hotel greeting/signature line for the email footer
 * @param logoUrl         optional per-hotel logo image URL rendered in the email header
 */
public record CheckoutNotificationRequest(
        @NotBlank @Email String guestEmail,
        @NotBlank String guestName,
        String hotelName,
        String roomNumber,
        @NotNull LocalDateTime actualCheckIn,
        @NotNull LocalDateTime actualCheckOut,
        List<InvoiceLineItemDto> lines,
        BigDecimal totalAmount,
        String currency,
        String locale,
        String customSubject,
        String greetingText,
        String logoUrl) {

    /**
     * Compact constructor — defensive copy of the charge lines list.
     */
    public CheckoutNotificationRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
