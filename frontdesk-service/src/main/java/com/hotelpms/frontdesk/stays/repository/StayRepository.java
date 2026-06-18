package com.hotelpms.frontdesk.stays.repository;

import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Finds a paginated list of stays belonging to a specific hotel (multi-tenancy).
     * Used by {@code getAllStays} so the listing endpoint never returns another
     * hotel's stays (T-STAY-04).
     *
     * @param hotelId  the hotel UUID
     * @param pageable the pagination and sorting parameters
     * @return a page of stays for that hotel
     */
    Page<Stay> findByHotelId(UUID hotelId, Pageable pageable);

    /**
     * Finds a stay by ID, scoped to the given hotel (multi-tenancy).
     * Used by {@code getStayById} and {@code checkOut} so neither endpoint can
     * be used to read or mutate another hotel's stay via a guessed/enumerated
     * UUID (T-STAY-04, IDOR).
     *
     * @param id      the stay ID
     * @param hotelId the hotel UUID (tenant isolation)
     * @return the Optional containing the stay if found within that hotel
     */
    Optional<Stay> findByIdAndHotelId(UUID id, UUID hotelId);

    /**
     * Finds all stays for a reservation, scoped to the given hotel (multi-tenancy).
     * Used by the public {@code getStaysByReservationId} endpoint so a
     * cross-hotel reservationId cannot be used to enumerate another hotel's
     * stays (T-STAY-04, IDOR).
     *
     * @param reservationId the reservation ID
     * @param hotelId       the hotel UUID (tenant isolation)
     * @return list of stays for that reservation within that hotel
     */
    List<Stay> findAllByReservationIdAndHotelId(UUID reservationId, UUID hotelId);

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
