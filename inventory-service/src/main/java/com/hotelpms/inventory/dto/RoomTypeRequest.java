package com.hotelpms.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for Room Type.
 *
 * @param name         the name
 * @param description  the description
 * @param maxOccupancy the maximum occupancy
 * @param basePrice    the base price
 */
public record RoomTypeRequest(
                @NotBlank(message = "Name required") String name,

                String description,

                @NotNull @Positive Integer maxOccupancy,

                @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal basePrice) {
}
