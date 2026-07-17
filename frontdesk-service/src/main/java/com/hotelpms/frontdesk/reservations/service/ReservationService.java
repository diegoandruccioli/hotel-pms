package com.hotelpms.frontdesk.reservations.service;

import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for Reservation operations.
 */
public interface ReservationService {

    /**
     * Creates a new reservation.
     *
     * @param request the reservation request
     * @return the created reservation response
     */
    ReservationResponse createReservation(ReservationRequest request);

    /**
     * Retrieves a reservation by ID.
     *
     * @param id the reservation ID
     * @return the reservation response
     */
    ReservationResponse getReservationById(UUID id);

    /**
     * Retrieves a paginated list of all reservations.
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of reservation responses
     */
    Page<ReservationResponse> getAllReservations(Pageable pageable);

    /**
     * Updates an existing reservation.
     *
     * @param id      the reservation ID
     * @param request the updated reservation request
     * @return the updated reservation response
     */
    ReservationResponse updateReservation(UUID id, ReservationRequest request);

    /**
     * Deletes (soft) a reservation by ID.
     *
     * @param id the reservation ID
     */
    void deleteReservation(UUID id);

    /**
     * Updates reservation status and/or actual guests count.
     *
     * @param id           the reservation ID
     * @param status       the new status (optional)
     * @param actualGuests the new actual guests count (optional)
     * @return the updated reservation response
     */
    ReservationResponse updateStatusAndGuests(UUID id, ReservationStatus status, Integer actualGuests);

    /**
     * Checks whether a guest has any active (non-terminal) reservation in the
     * caller's hotel. Used by guest-service's GDPR Art. 17 erasure legal-hold
     * guard (T-GST-05).
     *
     * @param guestId the guest UUID
     * @return true if a non-terminal reservation exists for this guest
     */
    boolean hasActiveReservations(UUID guestId);

    /**
     * Returns the rooms that are both housekeeping-{@code CLEAN} and free of
     * any overlapping reservation for the given date range, scoped to the
     * authenticated hotel. {@code checkIn}/{@code checkOut} use the same
     * exclusive-checkout-day convention as reservation booking (a stay ending
     * on day X does not block a new stay starting on day X).
     *
     * @param checkIn  the check-in date (inclusive)
     * @param checkOut the check-out date (exclusive); must be after {@code checkIn}
     * @return the rooms available for that range, ordered by room number
     */
    List<RoomResponse> getAvailableRooms(LocalDate checkIn, LocalDate checkOut);

    /**
     * Retries the reservation-confirmed email for a reservation whose original attempt
     * failed (notification-service was unavailable). Clears {@code confirmationEmailFailed}
     * on success. Scoped to the authenticated hotel.
     *
     * @param id the reservation ID
     * @return the updated reservation response
     */
    ReservationResponse retryConfirmationEmail(UUID id);
}
