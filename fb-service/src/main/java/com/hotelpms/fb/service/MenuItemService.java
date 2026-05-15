package com.hotelpms.fb.service;

import com.hotelpms.fb.dto.MenuItemRequest;
import com.hotelpms.fb.dto.MenuItemResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for the food and beverage menu catalog.
 */
public interface MenuItemService {

    /**
     * Returns all active menu items for the given hotel.
     *
     * @param hotelId the hotel scope
     * @return list of active menu item DTOs
     */
    List<MenuItemResponse> getAll(UUID hotelId);

    /**
     * Creates a new menu item for the given hotel.
     *
     * @param hotelId the hotel scope
     * @param request the item payload
     * @return the created item
     */
    MenuItemResponse create(UUID hotelId, MenuItemRequest request);

    /**
     * Updates an existing menu item. Enforces hotel scope.
     *
     * @param hotelId the hotel scope
     * @param itemId  the item UUID
     * @param request the update payload
     * @return the updated item
     */
    MenuItemResponse update(UUID hotelId, UUID itemId, MenuItemRequest request);

    /**
     * Soft-deletes a menu item. Enforces hotel scope.
     *
     * @param hotelId the hotel scope
     * @param itemId  the item UUID
     */
    void delete(UUID hotelId, UUID itemId);
}
