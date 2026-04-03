package com.hotelpms.fb.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for an order item response.
 *
 * @param id        the ID of the order item
 * @param itemName  the name of the item
 * @param quantity  the quantity of the item
 * @param unitPrice the unit price of the item
 */
@Builder
public record OrderItemResponse(
        UUID id,
        String itemName,
        Integer quantity,
        BigDecimal unitPrice) {
}
