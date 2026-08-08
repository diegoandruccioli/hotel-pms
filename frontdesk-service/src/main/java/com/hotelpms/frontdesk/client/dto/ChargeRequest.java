package com.hotelpms.frontdesk.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for attaching a charge to a stay's open invoice in billing-service.
 * {@code type} is a plain string (not the shared billing-service enum) since
 * frontdesk-service and billing-service do not share domain types across the wire.
 *
 * @param type        the charge category, e.g. {@code ROOM_NIGHT}
 * @param description human-readable description of the charge
 * @param amount      the charge amount — always the fiscally authoritative total,
 *                    regardless of whether {@code unitPrice}/{@code nights} below
 *                    are present
 * @param referenceId optional cross-service reference (the stay UUID)
 * @param unitPrice   optional per-night price, for display/audit only; {@code
 *                    null} when the stay's nights weren't billed at a single
 *                    uniform rate (e.g. it crosses a rate-season boundary) —
 *                    never used to derive {@code amount}
 * @param nights      optional number of nights this charge covers, for display/audit only
 */
public record ChargeRequest(
        String type, String description, BigDecimal amount, UUID referenceId,
        BigDecimal unitPrice, Integer nights) {
}
