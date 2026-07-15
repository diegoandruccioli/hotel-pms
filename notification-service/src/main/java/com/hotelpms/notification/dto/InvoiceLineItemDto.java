package com.hotelpms.notification.dto;

import java.math.BigDecimal;

/**
 * A single charge line shown in the checkout email.
 *
 * @param description human-readable charge description
 * @param amount      the charge amount
 */
public record InvoiceLineItemDto(String description, BigDecimal amount) {
}
