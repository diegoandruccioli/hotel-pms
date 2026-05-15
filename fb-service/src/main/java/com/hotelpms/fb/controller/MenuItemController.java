package com.hotelpms.fb.controller;

import com.hotelpms.fb.dto.MenuItemRequest;
import com.hotelpms.fb.dto.MenuItemResponse;
import com.hotelpms.fb.service.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing the food and beverage menu catalog.
 * Read endpoints are available to all authenticated users.
 * Write endpoints require ADMIN or OWNER role.
 */
@RestController
@RequestMapping("/api/v1/fb/menu-items")
@RequiredArgsConstructor
@Slf4j
public class MenuItemController {

    private final MenuItemService menuItemService;

    /**
     * Returns all active menu items for the requesting hotel.
     *
     * @return a {@code 200 OK} response containing the list of active menu items
     */
    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> getAllMenuItems() {
        log.info("REST request to get all menu items");
        return ResponseEntity.ok(menuItemService.getAll(resolveHotelId()));
    }

    /**
     * Creates a new menu item for the requesting hotel.
     *
     * @param request the item payload
     * @return {@code 201 Created} with the new item
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<MenuItemResponse> createMenuItem(
            @NonNull @Valid @RequestBody final MenuItemRequest request) {
        log.info("REST request to create menu item: {}", request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(menuItemService.create(resolveHotelId(), request));
    }

    /**
     * Updates an existing menu item. Enforces hotel scope.
     *
     * @param id      the item UUID
     * @param request the update payload
     * @return {@code 200 OK} with the updated item
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<MenuItemResponse> updateMenuItem(
            @NonNull @PathVariable final UUID id,
            @NonNull @Valid @RequestBody final MenuItemRequest request) {
        log.info("REST request to update menu item {}", id);
        return ResponseEntity.ok(menuItemService.update(resolveHotelId(), id, request));
    }

    /**
     * Soft-deletes a menu item. Enforces hotel scope.
     *
     * @param id the item UUID
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenuItem(@NonNull @PathVariable final UUID id) {
        log.info("REST request to delete menu item {}", id);
        menuItemService.delete(resolveHotelId(), id);
    }

    private UUID resolveHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr) || hotelIdStr.isBlank()) {
            throw new IllegalStateException("HOTEL_ID_NOT_AVAILABLE");
        }
        return UUID.fromString(hotelIdStr);
    }
}
