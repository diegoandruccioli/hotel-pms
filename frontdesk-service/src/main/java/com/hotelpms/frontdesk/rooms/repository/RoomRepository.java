package com.hotelpms.frontdesk.rooms.repository;

import com.hotelpms.frontdesk.rooms.domain.Room;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Same lookup as {@link #findByIdAndActiveTrueAndHotelId}, but takes a
     * {@code SELECT ... FOR UPDATE} row lock held for the rest of the caller's
     * transaction.
     *
     * <p>Used wherever a room's {@code OCCUPIED} status is read as a guard
     * condition before writing a new status (round 1 bug #4 follow-up): without
     * this lock, a concurrent check-in/check-out Saga write could commit between
     * the guard's read and its write, letting a housekeeping update silently
     * overwrite a room the Saga just marked {@code OCCUPIED} — the exact bypass
     * the guard exists to prevent. Postgres blocks any other transaction's
     * {@code UPDATE} on the same row until this lock is released, so the
     * Saga's write either lands before this read (guard sees it and rejects) or
     * after this transaction commits (Saga wins, as intended).
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID extracted from the authenticated user's JWT
     * @return the optional room, locked for update
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id AND r.active = true AND r.hotelId = :hotelId")
    Optional<Room> findByIdAndActiveTrueAndHotelIdForUpdate(@Param("id") UUID id, @Param("hotelId") UUID hotelId);

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
