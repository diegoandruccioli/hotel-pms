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

    /**
     * Returns a page of active rooms scoped to the given hotel, filtered by
     * housekeeping status. Backs the {@code status}-only case of {@code
     * getAllRooms}'s room-listing filters.
     *
     * @param hotelId  the hotel UUID extracted from the authenticated user's JWT
     * @param status   the housekeeping status to filter by
     * @param pageable the pagination and sorting parameters
     * @return a page of matching active rooms for that hotel
     */
    Page<Room> findAllByActiveTrueAndHotelIdAndStatus(UUID hotelId, RoomStatus status, Pageable pageable);

    /**
     * Finds all active rooms for a hotel EXCLUDING a given housekeeping status,
     * unpaginated. Used as the candidate pool for date-scoped availability
     * checks on a future booking (T-ROOM-housekeeping-blind-spot): a room's
     * transient today-status (CLEAN vs. DIRTY) says nothing about how it will
     * be on a check-in date weeks or months away, so only {@code MAINTENANCE}
     * (a deliberate, non-transient "not sellable" state) should exclude a room
     * from a future date-range search — see {@code ReservationService
     * #getAvailableRooms}.
     *
     * @param hotelId       the hotel UUID extracted from the authenticated user's JWT
     * @param excludedStatus the housekeeping status to exclude (e.g. {@code MAINTENANCE})
     * @return active rooms for that hotel, excluding the given status
     */
    List<Room> findAllByActiveTrueAndHotelIdAndStatusNot(UUID hotelId, RoomStatus excludedStatus);

    /**
     * Returns a page of active rooms scoped to the given hotel, filtered by
     * room type. Backs the {@code roomTypeId}-only case of {@code
     * getAllRooms}'s room-listing filters.
     *
     * @param hotelId    the hotel UUID extracted from the authenticated user's JWT
     * @param roomTypeId the room type UUID to filter by
     * @param pageable   the pagination and sorting parameters
     * @return a page of matching active rooms for that hotel
     */
    Page<Room> findAllByActiveTrueAndHotelIdAndRoomTypeId(UUID hotelId, UUID roomTypeId, Pageable pageable);

    /**
     * Returns a page of active rooms scoped to the given hotel, filtered by
     * both housekeeping status and room type. Backs the combined-filter case
     * of {@code getAllRooms}'s room-listing filters.
     *
     * @param hotelId    the hotel UUID extracted from the authenticated user's JWT
     * @param status     the housekeeping status to filter by
     * @param roomTypeId the room type UUID to filter by
     * @param pageable   the pagination and sorting parameters
     * @return a page of matching active rooms for that hotel
     */
    Page<Room> findAllByActiveTrueAndHotelIdAndStatusAndRoomTypeId(
            UUID hotelId, RoomStatus status, UUID roomTypeId, Pageable pageable);

    /**
     * Counts active rooms per housekeeping status for a hotel, one row per
     * status present. Backs the day-sheet room-status breakdown
     * (E-DASHBOARD-1) — a single grouped query instead of downloading every
     * room and counting client-side.
     *
     * @param hotelId the hotel UUID
     * @return one {@link RoomStatusCount} per distinct status among that
     *         hotel's active rooms
     */
    @Query("SELECT r.status AS status, COUNT(r) AS count FROM Room r "
            + "WHERE r.hotelId = :hotelId AND r.active = true GROUP BY r.status")
    List<RoomStatusCount> countRoomsByStatusForHotelId(@Param("hotelId") UUID hotelId);

    /**
     * Counts active rooms for a hotel — the denominator for available
     * room-nights in the KPI occupancy report (epic C4).
     *
     * @param hotelId the hotel UUID
     * @return the number of active rooms for that hotel
     */
    long countByActiveTrueAndHotelId(UUID hotelId);
}
