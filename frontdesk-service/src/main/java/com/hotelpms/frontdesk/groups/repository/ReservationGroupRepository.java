package com.hotelpms.frontdesk.groups.repository;

import com.hotelpms.frontdesk.groups.domain.ReservationGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ReservationGroup}, scoped by hotel (multi-tenant).
 */
public interface ReservationGroupRepository extends JpaRepository<ReservationGroup, UUID> {

    /**
     * Finds a group by ID scoped to a specific hotel (IDOR-safe).
     *
     * @param id      the group UUID
     * @param hotelId the hotel UUID extracted from the authenticated request
     * @return an Optional containing the group if it belongs to the hotel
     */
    Optional<ReservationGroup> findByIdAndHotelId(UUID id, UUID hotelId);

    /**
     * Returns all active groups belonging to a specific hotel, paginated,
     * most recent check-in date first.
     *
     * @param hotelId  the hotel UUID
     * @param pageable pagination/sorting parameters
     * @return a page of groups for that hotel
     */
    Page<ReservationGroup> findAllByHotelId(UUID hotelId, Pageable pageable);
}
