package com.hotelpms.inventory.service;

import com.hotelpms.inventory.dto.RoomTypeRequest;
import com.hotelpms.inventory.dto.RoomTypeResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for RoomType.
 */
public interface RoomTypeService {
    /**
     * Creates a room type.
     * 
     * @param request the request
     * @return the response
     */
    RoomTypeResponse createRoomType(RoomTypeRequest request);

    /**
     * Gets a room type by id.
     * 
     * @param id the id
     * @return the response
     */
    RoomTypeResponse getRoomTypeById(UUID id);

    /**
     * Gets all room types.
     * 
     * @return the list of responses
     */
    List<RoomTypeResponse> getAllRoomTypes();

    /**
     * Updates a room type.
     * 
     * @param id      the id
     * @param request the request
     * @return the response
     */
    RoomTypeResponse updateRoomType(UUID id, RoomTypeRequest request);

    /**
     * Deletes a room type.
     * 
     * @param id the id
     */
    void deleteRoomType(UUID id);
}
