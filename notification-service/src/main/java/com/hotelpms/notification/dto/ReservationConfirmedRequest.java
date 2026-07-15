package com.hotelpms.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Payload for a reservation-confirmed notification.
 *
 * @param guestEmail    the guest's email address
 * @param guestName     full name of the guest
 * @param hotelName     display name of the hotel property
 * @param roomDetails   human-readable room summary (type and number)
 * @param checkInDate   the reservation check-in date
 * @param checkOutDate  the reservation check-out date
 * @param nights        number of nights
 * @param reservationId the reservation UUID as string (for reference in the email)
 * @param locale        BCP-47 language tag used to select the template ("it" or "en")
 * @param customSubject optional per-hotel subject override; blank/null uses the default
 * @param greetingText  optional per-hotel greeting/signature line for the email footer
 * @param logoUrl       optional per-hotel logo image URL rendered in the email header
 */
public record ReservationConfirmedRequest(
        @NotBlank @Email String guestEmail,
        @NotBlank String guestName,
        String hotelName,
        String roomDetails,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @Positive int nights,
        String reservationId,
        String locale,
        String customSubject,
        String greetingText,
        String logoUrl) {
}
