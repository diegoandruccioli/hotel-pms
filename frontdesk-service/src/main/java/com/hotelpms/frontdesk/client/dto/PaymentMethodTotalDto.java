package com.hotelpms.frontdesk.client.dto;

import java.math.BigDecimal;

/**
 * One line of billing-service's cash-closing summary. {@code paymentMethod}
 * is deserialized as a plain string, not a shared enum — frontdesk-service
 * deliberately doesn't depend on billing-service's {@code PaymentMethod}
 * type across the service boundary.
 *
 * @param paymentMethod the payment method name, as reported by billing-service
 * @param total         the summed amount for this method
 */
public record PaymentMethodTotalDto(String paymentMethod, BigDecimal total) {
}
