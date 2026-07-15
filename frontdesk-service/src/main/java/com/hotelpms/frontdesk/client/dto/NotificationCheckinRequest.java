package com.hotelpms.frontdesk.client.dto;

import java.time.LocalDate;

/**
 * Request body sent to {@code POST /internal/notifications/checkin}.
 *
 * @param guestEmail           recipient address
 * @param guestName            guest full name for personalisation
 * @param hotelName            hotel display name
 * @param roomNumber           assigned room number
 * @param expectedCheckOutDate expected departure date
 * @param locale               BCP 47 locale tag (e.g. "it", "en")
 */
public record NotificationCheckinRequest(
        String guestEmail,
        String guestName,
        String hotelName,
        String roomNumber,
        LocalDate expectedCheckOutDate,
        String locale) {
}
