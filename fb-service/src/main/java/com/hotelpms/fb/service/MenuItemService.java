package com.hotelpms.fb.service;

import com.hotelpms.fb.dto.MenuItemResponse;

import java.util.List;

/**
 * Service interface for reading the food and beverage menu catalog.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface MenuItemService {

    /**
     * Returns all active menu items.
     *
     * @return a list of active menu item DTOs
     */
    List<MenuItemResponse> getAll();
}
