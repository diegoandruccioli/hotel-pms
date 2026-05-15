package com.hotelpms.fb.repository;

import com.hotelpms.fb.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for MenuItem.
 */
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    /**
     * Returns all active menu items for the given hotel.
     *
     * @param hotelId the hotel scope
     * @return list of active menu items
     */
    List<MenuItem> findAllByHotelId(UUID hotelId);

    /**
     * Finds a menu item by ID and hotel scope.
     *
     * @param id      the item UUID
     * @param hotelId the hotel scope
     * @return the item wrapped in Optional
     */
    Optional<MenuItem> findByIdAndHotelId(UUID id, UUID hotelId);
}
