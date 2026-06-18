package com.hotelpms.frontdesk.reservations.service;

import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}
