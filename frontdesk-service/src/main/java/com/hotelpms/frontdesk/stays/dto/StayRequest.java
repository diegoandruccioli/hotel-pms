package com.hotelpms.frontdesk.stays.dto;

import com.hotelpms.frontdesk.stays.domain.StayStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for creating or updating a Stay.
 *
 * <p>When {@code reservationId} is {@code null} the check-in is treated as a
 * <em>walk-in</em>: no reservation is validated, and {@code expectedCheckOutDate}
 * is used directly instead of being fetched from the reservation.
 *
 * @param hotelId              the hotel identifier for multi-tenancy
 * @param reservationId        the associated reservation ID; {@code null} for walk-in
 * @param guestId              the primary guest ID
 * @param roomId               the assigned room ID
 * @param status               the stay status
 * @param expectedCheckOutDate the expected check-out date; required for walk-in
 * @param actualCheckInTime    the actual check-in time
 * @param actualCheckOutTime   the actual check-out time
 * @param guests               the list of guests with Alloggiati fields
 */
public record StayRequest(
                UUID hotelId,

                UUID reservationId,

                @NotNull(message = "Guest ID is required") UUID guestId,

                @NotNull(message = "Room ID is required") UUID roomId,

                @NotNull(message = "Stay status is required") StayStatus status,

                LocalDate expectedCheckOutDate,

                LocalDateTime actualCheckInTime,
                LocalDateTime actualCheckOutTime,
                List<StayGuestRequest> guests) {
    /**
     * Compact constructor to ensure defensive copying of the guests list.
     */
    public StayRequest {
        guests = guests == null ? null : List.copyOf(guests);
    }

    /**
     * Returns a copy of the guests list to prevent external modification.
     *
     * @return the list of guests
     */
    @Override
    public List<StayGuestRequest> guests() {
        return guests == null ? null : List.copyOf(guests);
    }
}
