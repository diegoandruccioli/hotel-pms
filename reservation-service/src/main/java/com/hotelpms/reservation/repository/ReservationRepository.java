package com.hotelpms.reservation.repository;

import com.hotelpms.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Reservation.
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
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
    @Query("""
            SELECT r FROM Reservation r 
            JOIN r.lineItems li 
            WHERE r.id != :excludeId 
            AND r.active = true 
            AND r.status NOT IN (com.hotelpms.reservation.domain.ReservationStatus.CANCELLED,
                                 com.hotelpms.reservation.domain.ReservationStatus.NO_SHOW)
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
    @Query("""
            SELECT r FROM Reservation r 
            JOIN r.lineItems li 
            WHERE r.active = true 
            AND r.status NOT IN (com.hotelpms.reservation.domain.ReservationStatus.CANCELLED,
                                 com.hotelpms.reservation.domain.ReservationStatus.NO_SHOW)
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
}
