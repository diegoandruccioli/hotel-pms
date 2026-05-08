package com.hotelpms.fb.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal request DTO sent to billing-service to add a charge to an open invoice.
 *
 * @param type        the charge type (e.g. "FB_ORDER")
 * @param description human-readable charge description
 * @param amount      the charge amount
 * @param referenceId cross-service reference UUID (e.g. restaurant order ID)
 */
public record ChargeRequest(String type, String description, BigDecimal amount, UUID referenceId) {
}
