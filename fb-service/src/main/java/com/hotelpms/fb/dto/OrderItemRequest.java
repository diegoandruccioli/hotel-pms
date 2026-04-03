package com.hotelpms.fb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * DTO for an item within a new restaurant order request.
 *
 * @param itemName  the name of the item
 * @param quantity  the quantity of the item
 * @param unitPrice the unit price of the item
 */
@Builder
public record OrderItemRequest(
                @NotBlank(message = "Required") String itemName,

                @NotNull(message = "Required") @Positive(message = "Must be > 0") Integer quantity,

                @NotNull(message = "Required") @Positive(message = "Must be > 0") BigDecimal unitPrice) {
}
