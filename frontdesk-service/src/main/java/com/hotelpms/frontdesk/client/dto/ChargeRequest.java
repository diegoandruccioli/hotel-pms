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
 * @param amount      the charge amount
 * @param referenceId optional cross-service reference (the stay UUID)
 */
public record ChargeRequest(String type, String description, BigDecimal amount, UUID referenceId) {
}
