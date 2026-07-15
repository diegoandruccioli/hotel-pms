package com.hotelpms.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Payload for a check-in notification.
 *
 * @param guestEmail          the guest's email address
 * @param guestName           full name of the guest
 * @param hotelName           display name of the hotel property
 * @param roomNumber          the room number assigned at check-in
 * @param expectedCheckOutDate the expected check-out date
 * @param locale              BCP-47 language tag used to select the template ("it" or "en")
 */
public record CheckinNotificationRequest(
        @NotBlank @Email String guestEmail,
        @NotBlank String guestName,
        String hotelName,
        String roomNumber,
        @NotNull LocalDate expectedCheckOutDate,
        String locale) {
}
