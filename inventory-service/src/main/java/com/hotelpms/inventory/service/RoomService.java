package com.hotelpms.inventory.service;

import com.hotelpms.inventory.domain.RoomStatus;
import com.hotelpms.inventory.dto.RoomRequest;
import com.hotelpms.inventory.dto.RoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service interface for Room.
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
     * Gets a room by id.
     *
     * @param id the id
     * @return the response
     */
    RoomResponse getRoomById(UUID id);

    /**
     * Gets a paginated list of all active rooms.
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of room responses
     */
    Page<RoomResponse> getAllRooms(Pageable pageable);

    /**
     * Updates a room.
     *
     * @param id      the id
     * @param request the request
     * @return the response
     */
    RoomResponse updateRoom(UUID id, RoomRequest request);

    /**
     * Updates only the housekeeping status of a room.
     *
     * @param id     the room id
     * @param status the new room status
     * @return the updated room response
     */
    RoomResponse updateRoomStatus(UUID id, RoomStatus status);

    /**
     * Deletes a room.
     *
     * @param id the id
     */
    void deleteRoom(UUID id);
}
