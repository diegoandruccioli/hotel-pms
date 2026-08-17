package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.ChargeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO representing a single line-item charge on an invoice.
 *
 * @param id          the charge UUID
 * @param invoiceId   the parent invoice UUID
 * @param type        the charge category (ROOM_NIGHT, FB_ORDER, EXTRA)
 * @param description human-readable description of the charge
 * @param amount      the charge amount
 * @param vatRate     the VAT rate applied (e.g. 0.10 = 10%, 0.22 = 22%)
 * @param naturaCode  FatturaPA {@code Natura} code (e.g. {@code "N1"}) for a charge
 *                    out of VAT scope; {@code null} for every ordinary taxable charge
 * @param referenceId optional cross-service reference (order UUID, stay UUID, etc.)
 * @param unitPrice   optional per-night price, display/audit only; may be {@code null}
 * @param nights      optional number of nights this charge covers; may be {@code null}
 * @param createdAt   the timestamp when the charge was recorded
 */
public record ChargeResponse(
        UUID id,
        UUID invoiceId,
        ChargeType type,
        String description,
        BigDecimal amount,
        BigDecimal vatRate,
        String naturaCode,
        UUID referenceId,
        BigDecimal unitPrice,
        Integer nights,
        LocalDateTime createdAt) {
}
