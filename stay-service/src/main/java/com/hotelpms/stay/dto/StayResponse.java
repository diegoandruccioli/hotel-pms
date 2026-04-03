package com.hotelpms.stay.dto;

import com.hotelpms.stay.domain.StayStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for Stay responses.
 *
 * @param id                 the stay ID
 * @param hotelId            the hotel identifier
 * @param reservationId      the reservation ID
 * @param guestId            the guest ID
 * @param roomId             the room ID
 * @param status             the stay status
 * @param actualCheckInTime  the actual check in time
 * @param actualCheckOutTime the actual check out time
 * @param createdAt          the creation timestamp
 * @param updatedAt          the last update timestamp
 * @param guests             the list of guests
 */
public record StayResponse(
                UUID id,
                UUID hotelId,
                UUID reservationId,
                UUID guestId,
                UUID roomId,
                StayStatus status,
                LocalDateTime actualCheckInTime,
                LocalDateTime actualCheckOutTime,
                LocalDateTime createdAt,
                LocalDateTime updatedAt,
                List<StayGuestResponse> guests) {
    /**
     * Compact constructor to ensure defensive copying of the guests list.
     */
    public StayResponse {
        guests = guests == null ? null : List.copyOf(guests);
    }

    /**
     * Returns a copy of the guests list to prevent external modification.
     *
     * @return the list of guests
     */
    @Override
    public List<StayGuestResponse> guests() {
        return guests == null ? null : List.copyOf(guests);
    }
}
