package com.hotelpms.frontdesk.reservations.service;

import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservedRoomCharge;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * Combinable search over the caller's hotel reservations (C12): an optional
     * {@code upcomingOnly} filter (check-in date today or later), an optional
     * check-in date range, an optional status filter, and an optional free-text
     * query matched against the associated guest's name/email (resolved via a
     * cross-service call to guest-service, since Reservation only stores a
     * guestId). Results include {@code guestFullName}, batch-resolved for the
     * returned page only — same pattern as {@link #getAllReservations(Pageable)}.
     *
     * @param query        optional free-text query (guest name/email), or {@code null}/blank
     *                     to skip it
     * @param upcomingOnly if {@code true}, only reservations with check-in today or later;
     *                     ignored when {@code dateFrom} is set
     * @param dateFrom     optional lower bound (inclusive) on check-in date; overrides
     *                     {@code upcomingOnly} when set
     * @param dateTo       optional upper bound (inclusive) on check-in date
     * @param status       optional reservation status filter
     * @param pageable     pagination and sorting parameters
     * @return a page of matching reservation responses, scoped to the authenticated hotel
     */
    Page<ReservationResponse> searchReservations(String query, boolean upcomingOnly,
            LocalDate dateFrom, LocalDate dateTo, ReservationStatus status, Pageable pageable);

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
     * @param id            the reservation ID
     * @param status        the new status (optional)
     * @param actualGuests  the new actual guests count (optional)
     * @param clientVersion the version the caller last read, for optimistic-lock
     *                      conflict detection; {@code null} skips the check
     *                      (same backward-compatible contract as {@link
     *                      #updateReservation})
     * @return the updated reservation response
     */
    ReservationResponse updateStatusAndGuests(UUID id, ReservationStatus status, Integer actualGuests,
            Long clientVersion);

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
     * Reports whether {@code roomId} has an overlapping reservation in {@code [checkIn,
     * checkOut)}, regardless of the room's current housekeeping status — unlike {@link
     * #getAvailableRooms}, which also requires housekeeping-{@code CLEAN} and is therefore
     * unusable for a room the caller already knows is occupied. Used by a stay extension
     * (Parte 3): the room being extended is occupied by definition, so only a genuine
     * future booking conflict — never its own current occupancy — should block it.
     *
     * @param roomId   the room to check
     * @param checkIn  the range start (inclusive)
     * @param checkOut the range end (exclusive)
     * @return {@code true} if another reservation overlaps the range for this room
     */
    boolean isRoomBookedByOthers(UUID roomId, LocalDate checkIn, LocalDate checkOut);

    /**
     * Moves the reservation line item that currently reserves {@code oldRoomId}
     * to {@code newRoomId}, re-pricing it for the new room's rate. Called
     * exclusively by {@code StayServiceImpl.changeRoom} (Parte 6) right after
     * it moves the corresponding {@code Stay}'s own room — the only
     * legitimate way to change the room on a reservation once it already has
     * a {@code CHECKED_IN} stay.
     *
     * <p><strong>Deliberately bypasses</strong> the {@code
     * RESERVATION_HAS_ACTIVE_STAY} guard {@code updateReservation} enforces:
     * that guard exists to reject a *manual* edit through the reservation
     * editor once check-in has happened, precisely because the stay's own
     * room is otherwise never re-synced from it. This method IS that sync —
     * without it, the vacated room would stay "phantom-booked" against every
     * future overlap check (which reads only {@code reservation_line_items},
     * never {@code stays.room_id}), and the new room would stay invisible to
     * them.
     *
     * @param reservationId the reservation owning the stay being moved
     * @param oldRoomId     the room being vacated
     * @param newRoomId     the room being moved into
     * @param hotelId       the authenticated hotel, for tenant scoping
     */
    void syncLineItemRoomForCheckedInStay(UUID reservationId, UUID oldRoomId, UUID newRoomId, UUID hotelId);

    /**
     * Retries the reservation-confirmed email for a reservation whose original attempt
     * failed (notification-service was unavailable). Clears {@code confirmationEmailFailed}
     * on success. Scoped to the authenticated hotel.
     *
     * @param id the reservation ID
     * @return the updated reservation response
     */
    ReservationResponse retryConfirmationEmail(UUID id);

    /**
     * Returns the snapshotted price and night count for a specific room within a
     * reservation, resolved once by {@code RatePricingService} and stored on the
     * {@code ReservationLineItem} at booking time. Consumed by {@code
     * StayBillingCoordinator} at check-in: reading this (instead of recomputing
     * a price live from {@code RoomType.basePrice}) is what guarantees a guest is
     * billed exactly what they were quoted at booking — the reconciliation this
     * closes.
     *
     * <p>{@code nights} is derived from the reservation's own {@code
     * checkInDate}/{@code checkOutDate} — the same dates the price was computed
     * from — deliberately not from the stay's {@code actualCheckInTime}, which
     * can differ (late/early arrival) and would otherwise make the invoice
     * description disagree with the amount actually charged.
     *
     * @param reservationId the reservation UUID
     * @param roomId        the room UUID (one reservation may have several rooms/line items)
     * @param hotelId       the hotel UUID (multi-tenant scoping)
     * @return the snapshotted price and nights, or empty if the reservation or a
     *         matching active line item for that room does not exist
     */
    Optional<ReservedRoomCharge> getReservedRoomCharge(UUID reservationId, UUID roomId, UUID hotelId);

    /**
     * Creates a reservation using prices already resolved and frozen elsewhere
     * (an accepted {@code Quotation}) instead of resolving them live via
     * {@code RatePricingService} — the price a guest was quoted must be
     * exactly the price they're booked at, even if seasonal rates changed
     * since the quotation was sent. Availability is still verified at
     * creation time (a quotation does not hold inventory).
     *
     * @param guestId        the guest UUID
     * @param checkInDate    the check-in date (inclusive)
     * @param checkOutDate   the check-out date (exclusive)
     * @param expectedGuests expected number of guests; defaults to 1 if null
     * @param roomPrices     the frozen price for each room id
     * @return the created reservation response
     */
    ReservationResponse createReservationFromPricedRooms(UUID guestId, LocalDate checkInDate,
            LocalDate checkOutDate, Integer expectedGuests, Map<UUID, BigDecimal> roomPrices);
}
