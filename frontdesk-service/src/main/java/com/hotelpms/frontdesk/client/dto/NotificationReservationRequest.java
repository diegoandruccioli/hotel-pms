package com.hotelpms.frontdesk.client.dto;

import java.time.LocalDate;

/**
 * Request body sent to {@code POST /internal/notifications/reservation-confirmed}.
 *
 * @param guestEmail    recipient address
 * @param guestName     guest full name for personalisation
 * @param hotelName     hotel display name
 * @param roomDetails   human-readable room description
 * @param checkInDate   booked check-in date
 * @param checkOutDate  booked check-out date
 * @param nights        number of nights
 * @param reservationId human-readable reservation reference
 * @param locale        BCP 47 locale tag (e.g. "it", "en")
 * @param customSubject optional per-hotel subject override; blank/null uses the default
 * @param greetingText  optional per-hotel greeting/signature line for the email footer
 * @param logoUrl       optional per-hotel logo image URL rendered in the email header
 */
public record NotificationReservationRequest(
        String guestEmail,
        String guestName,
        String hotelName,
        String roomDetails,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int nights,
        String reservationId,
        String locale,
        String customSubject,
        String greetingText,
        String logoUrl) {
}
