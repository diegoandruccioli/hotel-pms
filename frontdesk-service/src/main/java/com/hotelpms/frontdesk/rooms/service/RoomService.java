package com.hotelpms.frontdesk.rooms.service;

import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
     * Creates a room.
     *
     * @param request the request
     * @return the response
     */
    RoomResponse createRoom(RoomRequest request);

    /**
     * Gets a room by id scoped to the authenticated hotel.
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     * @return the response
     */
    RoomResponse getRoomById(UUID id, UUID hotelId);

    /**
     * Gets a paginated list of all active rooms.
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of room responses
     */
    Page<RoomResponse> getAllRooms(Pageable pageable);

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
     * Updates only the housekeeping status of a room scoped to the authenticated hotel.
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     * @param status  the new room status
     * @return the updated room response
     */
    RoomResponse updateRoomStatus(UUID id, UUID hotelId, RoomStatus status);

    /**
     * Deletes a room scoped to the authenticated hotel.
     *
     * @param id      the room UUID
     * @param hotelId the hotel UUID (from the authenticated user's JWT)
     */
    void deleteRoom(UUID id, UUID hotelId);
}
