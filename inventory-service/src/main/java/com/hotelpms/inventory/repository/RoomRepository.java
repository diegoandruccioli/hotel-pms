package com.hotelpms.inventory.repository;

import com.hotelpms.inventory.domain.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Room.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    /**
     * Finds active room by room number.
     *
     * @param roomNumber the room number
     * @return the optional room
     */
    Optional<Room> findByRoomNumberAndActiveTrue(String roomNumber);

    /**
     * Returns a page of active rooms.
     * The {@code WHERE active = true} filter is applied at the database level via
     * the
     * derived-query method name, combining with the {@code @SQLRestriction} on the
     * entity
     * for double safety. Using a derived query (instead of
     * {@code findAll(Pageable)}) ensures
     * the pagination COUNT query also filters on {@code active = true}, giving an
     * accurate
     * total-elements count.
     *
     * @param pageable the pagination and sorting parameters
     * @return a page of active rooms
     */
    Page<Room> findAllByActiveTrue(Pageable pageable);
}
