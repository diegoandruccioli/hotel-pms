package com.hotelpms.stay.dto;

import com.hotelpms.stay.domain.StayStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for creating or updating a Stay.
 *
 * @param hotelId            the hotel identifier for multi-tenancy
 * @param reservationId      the associated reservation ID
 * @param guestId            the associated guest ID
 * @param roomId             the associated room ID
 * @param status             the stay status
 * @param actualCheckInTime  the actual check-in time
 * @param actualCheckOutTime the actual check-out time
 * @param guests             the list of guests
 */
public record StayRequest(
                UUID hotelId,

                @NotNull(message = "Reservation ID is required") UUID reservationId,

                @NotNull(message = "Guest ID is required") UUID guestId,

                @NotNull(message = "Room ID is required") UUID roomId,

                @NotNull(message = "Stay status is required") StayStatus status,

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
