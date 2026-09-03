package com.hotelpms.frontdesk.reservations.repository;

import com.hotelpms.internalauth.architecture.TenantScopeExempt;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Reservation.
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Finds a reservation by ID scoped to a specific hotel (IDOR-safe).
     *
     * @param id      reservation UUID
     * @param hotelId the hotel UUID extracted from the authenticated request
     * @return an Optional containing the reservation if it belongs to the hotel
     */
    Optional<Reservation> findByIdAndHotelId(UUID id, UUID hotelId);

    /**
     * Returns all active reservations belonging to a specific hotel, paginated.
     *
     * @param hotelId  the hotel UUID
     * @param pageable pagination/sorting parameters
     * @return page of reservations scoped to the hotel
     */
    Page<Reservation> findAllByHotelId(UUID hotelId, Pageable pageable);

    /**
     * Finds overlapping reservations for a given list of rooms and date range,
     * excluding a specific reservation ID.
     *
     * @param roomIds   list of room IDs
     * @param excludeId reservation ID to exclude
     * @param checkIn   check-in date
     * @param checkOut  check-out date
     * @return list of overlapping reservations
     */
    @TenantScopeExempt(reason = "roomIds is always pre-validated per-hotel by "
            + "verifyRoomsAvailability(lineItems, hotelId) before this call (ReservationServiceImpl."
            + "updateReservation/createReservation) — a room UUID belongs to exactly one hotel, so "
            + "results can't cross tenants even without a hotel_id column in this query. excludeId "
            + "likewise comes from a reservation already resolved via findByIdAndHotelId.")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM Reservation r
            JOIN r.lineItems li
            WHERE r.id != :excludeId
            AND r.active = true
            AND r.status NOT IN (com.hotelpms.frontdesk.reservations.domain.ReservationStatus.CANCELLED,
                                 com.hotelpms.frontdesk.reservations.domain.ReservationStatus.NO_SHOW)
            AND li.roomId IN :roomIds
            AND li.active = true
            AND r.checkInDate < :checkOut
            AND r.checkOutDate > :checkIn
            """)
    List<Reservation> findOverlappingReservations(
            @Param("roomIds") List<UUID> roomIds,
            @Param("excludeId") UUID excludeId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );

    /**
     * Finds overlapping reservations for a given list of rooms and date range.
     *
     * @param roomIds  list of room IDs
     * @param checkIn  check-in date
     * @param checkOut check-out date
     * @return list of overlapping reservations
     */
    @TenantScopeExempt(reason = "Same as findOverlappingReservations: roomIds is always "
            + "pre-validated per-hotel by verifyRoomsAvailability(lineItems, hotelId) before this "
            + "call (ReservationServiceImpl.createReservation).")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM Reservation r
            JOIN r.lineItems li
            WHERE r.active = true
            AND r.status NOT IN (com.hotelpms.frontdesk.reservations.domain.ReservationStatus.CANCELLED,
                                 com.hotelpms.frontdesk.reservations.domain.ReservationStatus.NO_SHOW)
            AND li.roomId IN :roomIds
            AND li.active = true
            AND r.checkInDate < :checkOut
            AND r.checkOutDate > :checkIn
            """)
    List<Reservation> findOverlappingReservationsForNew(
            @Param("roomIds") List<UUID> roomIds,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );

    /**
     * Finds the room IDs (from a candidate set) that have an overlapping
     * reservation in the given date range. Read-only — unlike {@link
     * #findOverlappingReservations} and {@link #findOverlappingReservationsForNew},
     * this does not take a {@code PESSIMISTIC_WRITE} lock: it backs a plain
     * availability lookup (e.g. the dashboard "available rooms" view), not a
     * booking write path, and must not block concurrent reservation writes.
     *
     * @param roomIds  candidate room IDs to check
     * @param checkIn  check-in date
     * @param checkOut check-out date
     * @return distinct room IDs, among {@code roomIds}, that are booked for some
     *         part of the given range
     */
    @TenantScopeExempt(reason = "roomIds is always the caller's own hotel-scoped bookable-room set, "
            + "sourced from roomService.findBookableRooms(hotelId) in "
            + "ReservationServiceImpl.getAvailableRooms — never attacker-supplied.")
    @Query("""
            SELECT DISTINCT li.roomId FROM Reservation r
            JOIN r.lineItems li
            WHERE r.active = true
            AND r.status NOT IN (com.hotelpms.frontdesk.reservations.domain.ReservationStatus.CANCELLED,
                                 com.hotelpms.frontdesk.reservations.domain.ReservationStatus.NO_SHOW)
            AND li.roomId IN :roomIds
            AND li.active = true
            AND r.checkInDate < :checkOut
            AND r.checkOutDate > :checkIn
            """)
    List<UUID> findOverlappingRoomIds(
            @Param("roomIds") List<UUID> roomIds,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );

    /**
     * Checks whether a guest has any reservation in this hotel whose status is not
     * in the given (terminal) set. Used by the guest-service GDPR Art. 17 erasure
     * legal-hold guard (T-GST-05) to block deletion while a booking is still live.
     *
     * @param guestId          the guest UUID
     * @param hotelId          the hotel UUID
     * @param terminalStatuses statuses considered non-active (excluded from the check)
     * @return true if a non-terminal reservation exists for this guest in this hotel
     */
    boolean existsByGuestIdAndHotelIdAndStatusNotIn(UUID guestId, UUID hotelId, List<ReservationStatus> terminalStatuses);

    /**
     * Combinable search over a hotel's reservations (C12): optional set of guest IDs
     * pre-resolved from a free-text query (guest name/email search happens in
     * guest-service — this repository only knows guestId, see
     * {@code ReservationServiceImpl.searchReservations}). Left with no check-in-date
     * lower bound — see {@link #searchUpcomingReservationsByHotelId} for that case,
     * kept as a separate query rather than an optional parameter here (see that
     * method's javadoc for why).
     *
     * @param hotelId  the hotel UUID from the authenticated request (always applied)
     * @param query    non-null marker that a guest query was requested (drives the
     *                 {@code guestIds} filter); {@code null} to skip it entirely
     * @param guestIds guest IDs matching the query in guest-service; must be non-null
     *                 (empty when {@code query} is null, or when no guest matched)
     * @param pageable pagination and sorting parameters
     * @return a page of matching reservations scoped to the hotel
     */
    @Query("SELECT r FROM Reservation r WHERE r.hotelId = :hotelId "
            + "AND (:query IS NULL OR r.guestId IN :guestIds)")
    Page<Reservation> searchReservationsByHotelId(
            @Param("hotelId") UUID hotelId,
            @Param("query") String query,
            @Param("guestIds") List<UUID> guestIds,
            Pageable pageable);

    /**
     * Same search as {@link #searchReservationsByHotelId}, plus a mandatory lower
     * bound on check-in date (used for {@code upcomingOnly}).
     *
     * <p>Deliberately a separate query rather than an optional {@code checkInFrom}
     * parameter on the method above: an earlier single-query version used
     * {@code (:checkInFrom IS NULL OR r.checkInDate >= :checkInFrom)}, which binds
     * {@code :checkInFrom} to two separate JDBC parameter positions. With a real date
     * value, Postgres can't infer the first position's type from the second at
     * PREPARE time (it's only ever compared via {@code IS NULL}) and fails with
     * "could not determine data type of parameter". Wrapping both positions in an
     * explicit {@code CAST(... AS date)} "fixes" that but breaks the null case
     * instead — Hibernate binds the null parameter with a generic/bytea JDBC type
     * that Postgres then refuses to cast to {@code date}. Here {@code checkInFrom} is
     * always non-null, so it's a single ordinary bind position compared directly to
     * a typed column — no branching, no ambiguity, no cast needed.
     *
     * @param hotelId     the hotel UUID from the authenticated request (always applied)
     * @param checkInFrom lower bound on check-in date (inclusive); must be non-null
     * @param query       non-null marker that a guest query was requested (drives the
     *                    {@code guestIds} filter); {@code null} to skip it entirely
     * @param guestIds    guest IDs matching the query in guest-service; must be non-null
     *                    (empty when {@code query} is null, or when no guest matched)
     * @param pageable    pagination and sorting parameters
     * @return a page of matching reservations scoped to the hotel
     */
    @Query("SELECT r FROM Reservation r WHERE r.hotelId = :hotelId "
            + "AND r.checkInDate >= :checkInFrom "
            + "AND (:query IS NULL OR r.guestId IN :guestIds)")
    Page<Reservation> searchUpcomingReservationsByHotelId(
            @Param("hotelId") UUID hotelId,
            @Param("checkInFrom") LocalDate checkInFrom,
            @Param("query") String query,
            @Param("guestIds") List<UUID> guestIds,
            Pageable pageable);

    /**
     * Combinable search over a hotel's reservations, with an explicit check-in
     * date range and a status set — used by the C1.2 filtered listing (E-DASHBOARD-1
     * follow-up). Both date bounds and the status set are always non-null and
     * concretely valued (callers resolve "no filter" to the widest possible
     * range/full status set in {@code ReservationServiceImpl}) so this query never
     * needs an {@code IS NULL} branch on a date-typed parameter — see {@link
     * #searchUpcomingReservationsByHotelId} for why that pattern is avoided here.
     *
     * @param hotelId  the hotel UUID from the authenticated request (always applied)
     * @param dateFrom lower bound (inclusive) on check-in date
     * @param dateTo   upper bound (inclusive) on check-in date
     * @param statuses reservation statuses to include
     * @param query    non-null marker that a guest query was requested (drives the
     *                 {@code guestIds} filter); {@code null} to skip it entirely
     * @param guestIds guest IDs matching the query in guest-service; must be non-null
     *                 (empty when {@code query} is null, or when no guest matched)
     * @param pageable pagination and sorting parameters
     * @return a page of matching reservations scoped to the hotel
     */
    @Query("SELECT r FROM Reservation r WHERE r.hotelId = :hotelId "
            + "AND r.checkInDate BETWEEN :dateFrom AND :dateTo "
            + "AND r.status IN :statuses "
            + "AND (:query IS NULL OR r.guestId IN :guestIds)")
    Page<Reservation> filterReservationsByHotelId(
            @Param("hotelId") UUID hotelId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("query") String query,
            @Param("guestIds") List<UUID> guestIds,
            Pageable pageable);

    /**
     * Counts reservations checking in on a given date with one of the given
     * statuses, scoped to a hotel. Backs the day-sheet "arrivals today" count
     * (E-DASHBOARD-1) — previously computed client-side by downloading up to
     * 500 reservations and filtering in the browser.
     *
     * @param hotelId the hotel UUID
     * @param date    the check-in date
     * @param statuses the reservation statuses to count (e.g. CONFIRMED, PENDING)
     * @return the number of matching reservations
     */
    int countByHotelIdAndCheckInDateAndStatusIn(UUID hotelId, LocalDate date, Collection<ReservationStatus> statuses);

    /**
     * Counts reservations checking out on a given date with one of the given
     * statuses, scoped to a hotel. Backs the day-sheet "departures today" count.
     *
     * @param hotelId the hotel UUID
     * @param date    the check-out date
     * @param statuses the reservation statuses to count (e.g. CONFIRMED, CHECKED_IN, PENDING)
     * @return the number of matching reservations
     */
    int countByHotelIdAndCheckOutDateAndStatusIn(UUID hotelId, LocalDate date, Collection<ReservationStatus> statuses);
}
