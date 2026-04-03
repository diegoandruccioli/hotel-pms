package com.hotelpms.billing.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * External Data Transfer Object representing a Reservation response.
 *
 * @param id           the reservation UUID
 * @param guestId      the associated guest UUID
 * @param roomId       the associated room UUID
 * @param checkInDate  the check-in date
 * @param checkOutDate the check-out date
 * @param totalPrice   the total price computed for the reservation
 * @param status       the current reservation status
 */
public record ReservationResponse(
        UUID id,
        UUID guestId,
        UUID roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        BigDecimal totalPrice,
        String status) {
}
