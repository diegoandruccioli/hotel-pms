package com.hotelpms.frontdesk.rooms.repository;

import com.hotelpms.frontdesk.rooms.domain.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RoomType.
 */
@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {
    /**
     * Finds active room type by name.
     *
     * @param name the name
     * @return the optional room type
     */
    Optional<RoomType> findByNameAndActiveTrue(String name);
}
