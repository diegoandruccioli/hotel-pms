package com.hotelpms.frontdesk.client.dto;

import java.math.BigDecimal;

/**
 * Single charge line returned from billing-service as part of {@link InvoiceForEmailResponse}.
 *
 * @param description human-readable charge description
 * @param amount      charge amount (excl. VAT)
 */
public record ChargeLineDto(String description, BigDecimal amount) {
}
