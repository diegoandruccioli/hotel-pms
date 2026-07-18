package com.hotelpms.frontdesk.rooms.service;

import com.hotelpms.frontdesk.rooms.dto.RoomTypeRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for RoomType.
 */
public interface RoomTypeService {
    /**
     * Creates a room type, scoped to the caller's hotel (T-ROOM-02).
     *
     * @param request the request
     * @param hotelId the authenticated hotel UUID
     * @return the response
     */
    RoomTypeResponse createRoomType(RoomTypeRequest request, UUID hotelId);

    /**
     * Gets a room type by id, scoped to the caller's hotel (T-ROOM-02).
     *
     * @param id      the id
     * @param hotelId the authenticated hotel UUID; the room type must belong to it
     * @return the response
     */
    RoomTypeResponse getRoomTypeById(UUID id, UUID hotelId);

    /**
     * Gets all room types belonging to the caller's hotel (T-ROOM-02).
     *
     * @param hotelId the authenticated hotel UUID
     * @return the list of responses
     */
    List<RoomTypeResponse> getAllRoomTypes(UUID hotelId);

    /**
     * Updates a room type, scoped to the caller's hotel (T-ROOM-02).
     *
     * @param id      the id
     * @param hotelId the authenticated hotel UUID; the room type must belong to it
     * @param request the request
     * @return the response
     */
    RoomTypeResponse updateRoomType(UUID id, UUID hotelId, RoomTypeRequest request);

    /**
     * Deletes a room type, scoped to the caller's hotel (T-ROOM-02).
     *
     * @param id      the id
     * @param hotelId the authenticated hotel UUID; the room type must belong to it
     */
    void deleteRoomType(UUID id, UUID hotelId);
}
