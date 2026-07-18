package com.hotelpms.frontdesk.rooms.repository;

import com.hotelpms.frontdesk.rooms.domain.Room;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Room.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    /**
     * Returns a page of active rooms scoped to the given hotel (multi-tenancy).
     * Used by {@code getAllRooms} so the listing endpoint never returns
     * another hotel's rooms (T-ROOM-01, IDOR / cross-tenant data leak).
     *
     * @param hotelId  the hotel UUID extracted from the authenticated user's JWT
     * @param pageable the pagination and sorting parameters
     * @return a page of active rooms for that hotel
     */
    Page<Room> findAllByActiveTrueAndHotelId(UUID hotelId, Pageable pageable);

    /**
     * Finds an active room by its UUID scoped to the given hotel.
     * Enforces multi-tenant isolation: a room from hotel A cannot be
     * accessed by a user authenticated to hotel B.
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID extracted from the authenticated user's JWT
     * @return the optional room
     */
    Optional<Room> findByIdAndActiveTrueAndHotelId(UUID id, UUID hotelId);

    /**
     * Finds all active rooms for a hotel with a given housekeeping status,
     * unpaginated. Used as the candidate pool for date-scoped availability
     * checks (status alone is not date-aware, see {@code ReservationService
     * #getAvailableRooms}), where the full set must be intersected against
     * reservation overlaps rather than paged.
     *
     * @param hotelId the hotel UUID extracted from the authenticated user's JWT
     * @param status  the housekeeping status to filter by
     * @return active rooms for that hotel and status
     */
    List<Room> findAllByActiveTrueAndHotelIdAndStatus(UUID hotelId, RoomStatus status);
}
