package com.hotelpms.stay.repository;

import com.hotelpms.stay.domain.Stay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing Stay entities.
 */
@Repository
public interface StayRepository extends JpaRepository<Stay, UUID> {

    /**
     * Finds a stay by reservation ID.
     *
     * @param reservationId the reservation ID
     * @return the Optional containing the stay if found
     */
    Optional<Stay> findByReservationId(UUID reservationId);

    /**
     * Finds all stays by reservation ID.
     *
     * @param reservationId the reservation ID
     * @return list of stays for that reservation
     */
    List<Stay> findAllByReservationId(UUID reservationId);

    /**
     * Finds all stays belonging to a specific hotel (multi-tenancy).
     *
     * @param hotelId the hotel UUID
     * @return list of stays for that hotel
     */
    List<Stay> findByHotelId(UUID hotelId);

    /**
     * Finds all stays where actual check-in time falls within the given window.
     * Used by the Alloggiati Web police report to extract daily arrivals.
     *
     * @param start beginning of the time window (inclusive)
     * @param end   end of the time window (exclusive)
     * @return list of matching stays
     */
    List<Stay> findByActualCheckInTimeBetween(LocalDateTime start, LocalDateTime end);
}
