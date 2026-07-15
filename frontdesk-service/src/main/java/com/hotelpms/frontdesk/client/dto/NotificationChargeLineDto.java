package com.hotelpms.frontdesk.client.dto;

import java.math.BigDecimal;

/**
 * Single charge line forwarded to notification-service for checkout emails.
 *
 * @param description human-readable charge description
 * @param amount      charge amount
 */
public record NotificationChargeLineDto(String description, BigDecimal amount) {
}
