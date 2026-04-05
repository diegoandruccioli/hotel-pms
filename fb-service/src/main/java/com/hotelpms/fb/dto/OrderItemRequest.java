package com.hotelpms.fb.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.util.UUID;

/**
 * DTO for an item within a new restaurant order request.
 *
 * <p>Clients identify items by {@code menuItemId}; the unit price is resolved
 * server-side from the {@code menu_items} catalog. Supplying a price from the
 * client is intentionally not supported (T-FB-02 mitigation).
 *
 * @param menuItemId the UUID of the menu item from the server-side catalog
 * @param quantity   the number of portions requested
 */
@Builder
public record OrderItemRequest(
                @NotNull(message = "Menu item ID is required") UUID menuItemId,

                @NotNull(message = "Required") @Positive(message = "Must be > 0") Integer quantity) {
}
