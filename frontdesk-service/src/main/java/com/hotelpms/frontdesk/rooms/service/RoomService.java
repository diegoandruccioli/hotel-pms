package com.hotelpms.frontdesk.rooms.service;

import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Room.
 *
 * <p>Also the in-process integration point for the {@code reservations} and
 * {@code stays} domains within this service (formerly the {@code InventoryClient}
 * Feign client, before the frontdesk-service consolidation — ADR-001).
 */
public interface RoomService {

    /**
     * Creates a room scoped to the authenticated hotel.
     *
     * @param request the request
     * @param hotelId the hotel UUID (from the authenticated user's JWT);
     *                takes precedence over any {@code hotelId} present in the
     *                request body (T-ROOM-01)
     * @return the response
     */
    RoomResponse createRoom(RoomRequest request, UUID hotelId);

    /**
     * Gets a room by id scoped to the authenticated hotel.
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     * @return the response
     */
    RoomResponse getRoomById(UUID id, UUID hotelId);

    /**
     * Gets a paginated list of all active rooms belonging to the
     * authenticated hotel.
     *
     * @param pageable the pagination and sorting parameters
     * @param hotelId  the hotel UUID (from the authenticated user's JWT) (T-ROOM-01)
     * @return a page of room responses
     */
    Page<RoomResponse> getAllRooms(Pageable pageable, UUID hotelId);

    /**
     * Gets a paginated, filtered list of active rooms belonging to the
     * authenticated hotel: an optional housekeeping status and an optional
     * room type.
     *
     * @param pageable   the pagination and sorting parameters
     * @param hotelId    the hotel UUID (from the authenticated user's JWT) (T-ROOM-01)
     * @param status     optional housekeeping status filter
     * @param roomTypeId optional room type filter
     * @return a page of matching room responses
     */
    Page<RoomResponse> getAllRooms(Pageable pageable, UUID hotelId, RoomStatus status, UUID roomTypeId);

    /**
     * Updates a room scoped to the authenticated hotel.
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     * @param request the update request
     * @return the response
     */
    RoomResponse updateRoom(UUID id, UUID hotelId, RoomRequest request);

    /**
     * Updates a room's status without restriction — the trusted, Saga-internal
     * entry point. Used by {@code StayServiceImpl} to set {@code OCCUPIED} at
     * check-in and {@code DIRTY} at check-out; both legitimately transition a
     * room into or out of {@code OCCUPIED}, which is exactly what
     * {@link #updateHousekeepingStatus} must refuse from any other caller.
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     * @param status  the new room status
     * @return the updated room response
     */
    RoomResponse updateRoomStatus(UUID id, UUID hotelId, RoomStatus status);

    /**
     * Updates a room's housekeeping status on behalf of front-desk/housekeeping
     * staff via {@code PATCH /rooms/{id}/status} (round 1 bug #4). Unlike
     * {@link #updateRoomStatus}, this enforces the invariant documented on
     * {@link RoomStatus#OCCUPIED} itself: {@code OCCUPIED} can never be set
     * through this path, and a room that is currently {@code OCCUPIED} cannot
     * be reassigned to any other status — only the check-in Saga sets it, only
     * the check-out Saga clears it.
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     * @param status  the requested housekeeping status
     * @return the updated room response
     */
    RoomResponse updateHousekeepingStatus(UUID id, UUID hotelId, RoomStatus status);

    /**
     * Bulk variant of {@link #updateHousekeepingStatus}, applying the same
     * guarded status change to several rooms in one call — Housekeeping's
     * multi-select status change, replacing one request per room. All-or-nothing:
     * if any room fails (not found, or the same {@code OCCUPIED} guard
     * violations as the single-room path), the whole batch is rolled back.
     *
     * @param roomIds the room UUIDs to update
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     * @param status  the requested housekeeping status
     * @return the updated room responses, in the same order as {@code roomIds}
     */
    List<RoomResponse> updateHousekeepingStatusBulk(List<UUID> roomIds, UUID hotelId, RoomStatus status);

    /**
     * Deletes a room scoped to the authenticated hotel.
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     */
    void deleteRoom(UUID id, UUID hotelId);

    /**
     * Returns every active, {@code CLEAN} room belonging to the authenticated
     * hotel, unpaginated. {@code CLEAN} is the housekeeping precondition for
     * sellability; date-specific availability (no overlapping reservation) is
     * layered on top by the caller.
     *
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     * @return the clean rooms for that hotel
     */
    List<RoomResponse> findCleanRooms(UUID hotelId);
}
