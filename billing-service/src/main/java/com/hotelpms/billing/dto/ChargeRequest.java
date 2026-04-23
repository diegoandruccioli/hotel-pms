package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.ChargeType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for adding a charge to an invoice.
 *
 * @param type        the category of the charge (ROOM_NIGHT, FB_ORDER, EXTRA)
 * @param description human-readable description (e.g. "Espresso x2, Tiramisù x1")
 * @param amount      the charge amount; must be non-negative
 * @param referenceId optional cross-service reference (order UUID, stay UUID, etc.)
 */
public record ChargeRequest(

        @NotNull(message = "Charge type is required")
        ChargeType type,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Amount is required")
        @PositiveOrZero(message = "Amount must be >= 0")
        @Digits(integer = 10, fraction = 2,
                message = "Amount must have at most 10 integer digits and 2 decimal places")
        BigDecimal amount,

        UUID referenceId) {
}
