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
 * @param referenceId optional cross-service reference (order UUID, stay UUID, etc.)
 * @param createdAt   the timestamp when the charge was recorded
 */
public record ChargeResponse(
        UUID id,
        UUID invoiceId,
        ChargeType type,
        String description,
        BigDecimal amount,
        UUID referenceId,
        LocalDateTime createdAt) {
}
