package com.hotelpms.billing.repository;

import com.hotelpms.billing.domain.PaymentMethod;

import java.math.BigDecimal;

/**
 * Spring Data interface projection for a {@code GROUP BY paymentMethod} sum
 * over a hotel's payments on a given business date. Backs the night-audit
 * cash-closing summary — one query instead of one {@code SUM} per {@link
 * PaymentMethod} value.
 */
public interface PaymentMethodTotal {

    /**
     * The payment method this total is for.
     *
     * @return the payment method
     */
    PaymentMethod getPaymentMethod();

    /**
     * The summed payment amount for this method, for the queried hotel and date.
     *
     * @return the total amount
     */
    BigDecimal getTotal();
}
