package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.ChargeType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for adding a charge directly to a reservation group's master folio,
 * tagging which stay it was transferred from.
 *
 * @param type             the charge category, e.g. {@code ROOM_NIGHT}
 * @param description      human-readable description of the charge
 * @param amount           the charge amount; must be non-negative
 * @param unitPrice        optional per-night price, for display/audit only
 * @param nights           optional number of nights this charge covers, for display/audit only
 * @param routedFromStayId the stay (frontdesk-service) this charge is being transferred from
 */
public record GroupChargeRequest(

        @NotNull(message = "Charge type is required")
        ChargeType type,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Amount is required")
        @PositiveOrZero(message = "Amount must be >= 0")
        @Digits(integer = 10, fraction = 2,
                message = "Amount must have at most 10 integer digits and 2 decimal places")
        BigDecimal amount,

        @Digits(integer = 10, fraction = 2,
                message = "Unit price must have at most 10 integer digits and 2 decimal places")
        BigDecimal unitPrice,

        @Positive(message = "Nights must be positive")
        Integer nights,

        @NotNull(message = "routedFromStayId is required")
        UUID routedFromStayId) {
}
