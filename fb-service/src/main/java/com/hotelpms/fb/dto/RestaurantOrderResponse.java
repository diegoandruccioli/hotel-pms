package com.hotelpms.fb.dto;

import com.hotelpms.fb.domain.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for a restaurant order response.
 *
 * @param id          the ID of the order
 * @param stayId      the ID of the stay this order is associated with
 * @param orderDate   the date and time the order was placed
 * @param totalAmount the total amount of the order
 * @param status      the status of the order
 * @param items       the items included in the order
 * @param createdAt   the creation timestamp
 * @param updatedAt   the last update timestamp
 */
@Builder
public record RestaurantOrderResponse(
                UUID id,
                UUID stayId,
                LocalDateTime orderDate,
                BigDecimal totalAmount,
                OrderStatus status,
                List<OrderItemResponse> items,
                LocalDateTime createdAt,
                LocalDateTime updatedAt) {

        /**
         * Compact constructor to ensure defensive copying of the items list.
         *
         * @param id          the order ID
         * @param stayId      the stay ID
         * @param orderDate   the order date
         * @param totalAmount the total amount
         * @param status      the status
         * @param items       the items
         * @param createdAt   the creation time
         * @param updatedAt   the update time
         */
        public RestaurantOrderResponse {
                items = items == null ? null : List.copyOf(items);
        }

        /**
         * Returns an unmodifiable list of items.
         *
         * @return unmodifiable items list
         */
        @Override
        public List<OrderItemResponse> items() {
                return items == null ? null : List.copyOf(items);
        }
}
