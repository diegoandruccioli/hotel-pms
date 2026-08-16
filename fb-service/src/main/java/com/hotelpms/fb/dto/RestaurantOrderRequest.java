package com.hotelpms.fb.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * DTO for creating a new restaurant order.
 *
 * @param stayId the ID of the stay this order is associated with
 * @param items  the list of items in the order
 */
@Builder
public record RestaurantOrderRequest(
                @NotNull(message = "Stay ID must not be null") UUID stayId,

                @NotEmpty(message = "Order must contain at least one item")
                @Size(max = MAX_ITEMS, message = "Too many order lines") @Valid List<OrderItemRequest> items) {

        /**
         * Upper bound on distinct order lines per request (Finding #18,
         * security-report.md — LOW); per-line quantity already covers volume.
         */
        static final int MAX_ITEMS = 50;

        /**
         * Compact constructor to ensure defensive copying of the items list.
         *
         * @param stayId the stay ID
         * @param items  the items
         */
        public RestaurantOrderRequest {
                items = items == null ? null : List.copyOf(items);
        }

        /**
         * Returns an unmodifiable list of items.
         *
         * @return unmodifiable items list
         */
        @Override
        public List<OrderItemRequest> items() {
                return items == null ? null : List.copyOf(items);
        }
}
