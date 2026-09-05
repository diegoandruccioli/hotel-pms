package com.hotelpms.billing.dto;

import com.hotelpms.billing.domain.PaymentMethod;

import java.math.BigDecimal;

/**
 * One line of the night-audit cash-closing summary.
 *
 * @param paymentMethod the payment method
 * @param total         the summed amount recorded for this method on the queried date
 */
public record PaymentMethodTotalResponse(PaymentMethod paymentMethod, BigDecimal total) {
}
