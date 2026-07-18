package com.hotelpms.frontdesk.rooms.repository;

import com.hotelpms.frontdesk.rooms.domain.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RoomType.
 */
@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {

    /**
     * Finds an active room type by ID scoped to a specific hotel (T-ROOM-02, IDOR-safe).
     * Returns empty if the room type belongs to a different hotel, preventing
     * cross-tenant read/tamper access via UUID enumeration.
     *
     * @param id      the room type UUID
     * @param hotelId the hotel UUID from the authenticated request
     * @return the room type if it belongs to the given hotel
     */
    Optional<RoomType> findByIdAndHotelId(UUID id, UUID hotelId);

    /**
     * Returns all active room types belonging to a specific hotel (multi-tenancy).
     *
     * @param hotelId the hotel UUID
     * @return list of room types scoped to the hotel
     */
    List<RoomType> findAllByHotelId(UUID hotelId);
}
