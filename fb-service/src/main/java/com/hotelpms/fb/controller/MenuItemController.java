package com.hotelpms.fb.controller;

import com.hotelpms.fb.dto.MenuItemResponse;
import com.hotelpms.fb.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing the food and beverage menu catalog.
 */
@RestController
@RequestMapping("/api/v1/fb/menu-items")
@RequiredArgsConstructor
@Slf4j
public class MenuItemController {

    private final MenuItemService menuItemService;

    /**
     * Returns all active menu items.
     *
     * @return a {@code 200 OK} response containing the list of active menu items
     */
    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> getAllMenuItems() {
        log.info("REST request to get all menu items");
        return ResponseEntity.ok(menuItemService.getAll());
    }
}
