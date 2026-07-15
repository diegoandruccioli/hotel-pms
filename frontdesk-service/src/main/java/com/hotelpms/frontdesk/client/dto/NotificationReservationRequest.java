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
        String locale) {
}
