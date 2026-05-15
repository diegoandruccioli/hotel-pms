package com.hotelpms.fb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request payload for creating or updating a menu item.
 *
 * @param name        display name (required)
 * @param price       unit price in EUR (required, must be positive)
 * @param category    display category (required, e.g. "Bar", "Cucina")
 * @param description optional free-text description
 * @param available   whether the item is visible in the order form
 */
public record MenuItemRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal price,
        @NotBlank String category,
        String description,
        boolean available) {
}
