package com.hotelpms.frontdesk.reservations.repository;

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
}
