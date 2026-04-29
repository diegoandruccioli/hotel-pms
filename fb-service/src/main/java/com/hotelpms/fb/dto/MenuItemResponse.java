package com.hotelpms.fb.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for a food and beverage menu item.
 *
 * @param id    the unique identifier of the menu item
 * @param name  the display name (e.g. "Espresso", "Club Sandwich")
 * @param price the canonical price in EUR
 */
@Builder
public record MenuItemResponse(UUID id, String name, BigDecimal price) {
}
