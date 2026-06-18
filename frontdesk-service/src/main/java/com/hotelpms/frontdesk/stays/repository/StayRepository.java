package com.hotelpms.frontdesk.stays.repository;

import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
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

    /**
     * Finds the most recent stay for a guest with the given status,
     * ordered by actual check-in time descending.
     * Used to pre-fill the check-in form for returning guests, after the
     * caller has verified that the guest profile is still active in guest-service.
     *
     * @param guestId the guest UUID
     * @param status  the stay status to filter by
     * @return an Optional containing the most recent matching stay
     */
    Optional<Stay> findTopByGuestIdAndStatusOrderByActualCheckInTimeDesc(UUID guestId, StayStatus status);

    /**
     * Finds the most recent stay for a guest within a hotel, regardless of status.
     * Used by the guest-service GDPR legal-hold guard (T-GST-05) to verify whether
     * the TULPS five-year retention obligation has expired before anonymising a guest profile.
     *
     * @param guestId the guest UUID
     * @param hotelId the hotel UUID (tenant isolation)
     * @return the most recent stay if present
     */
    Optional<Stay> findTopByGuestIdAndHotelIdOrderByActualCheckInTimeDesc(
            @NonNull UUID guestId, @NonNull UUID hotelId);

    /**
     * Finds all stays for a guest within a hotel, ordered by check-in descending.
     * Used by the GDPR Art. 20 data-export endpoint to return full stay history.
     *
     * @param guestId the guest UUID
     * @param hotelId the hotel UUID (tenant isolation)
     * @return list of stays, most recent first
     */
    List<Stay> findByGuestIdAndHotelIdOrderByActualCheckInTimeDesc(
            @NonNull UUID guestId, @NonNull UUID hotelId);
}
