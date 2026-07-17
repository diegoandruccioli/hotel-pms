package com.hotelpms.frontdesk.stays.dto;

import com.hotelpms.frontdesk.stays.domain.StayStatus;

import java.time.LocalDate;
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
 * @param invoiceId               the billing invoice folio UUID opened at check-in (may be null)
 * @param alloggiatiSent          whether the Alloggiati report was submitted to the PS portal
 * @param alloggiatiSendFailed    whether the most recent Alloggiati submission attempt failed
 * @param alloggiatiFailureReason the error from the most recent failed attempt, or null
 * @param guests                  the list of guests
 * @param guestDisplayName        denormalized primary guest "Cognome Nome", null for legacy stays
 * @param roomNumber              denormalized room number, null for legacy stays
 * @param expectedCheckOutDate    expected check-out date sourced from the reservation (or the
 *                                walk-in request) at check-in time; null for legacy stays
 * @param invoiceCreationFailed         whether the most recent invoice-creation attempt at check-in failed
 * @param invoiceCreationFailureReason  the reason from the most recent failed attempt, or null
 * @param checkoutEmailFailed           whether the most recent checkout email attempt failed
 * @param checkoutEmailFailureReason    the reason from the most recent failed attempt, or null
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
                UUID invoiceId,
                boolean alloggiatiSent,
                boolean alloggiatiSendFailed,
                String alloggiatiFailureReason,
                List<StayGuestResponse> guests,
                String guestDisplayName,
                String roomNumber,
                LocalDate expectedCheckOutDate,
                boolean invoiceCreationFailed,
                String invoiceCreationFailureReason,
                boolean checkoutEmailFailed,
                String checkoutEmailFailureReason) {
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
