package com.hotelpms.billing.service;

import com.hotelpms.billing.dto.PaymentRequest;
import com.hotelpms.billing.dto.PaymentResponse;

import java.util.UUID;
import org.springframework.lang.NonNull;

/**
 * Service interface for managing Payments.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface PaymentService {

    /**
     * Adds a payment to a specific invoice.
     * 
     * @param invoiceId the invoice UUID
     * @param request   the payment request
     * @return the recorded payment response
     */
    PaymentResponse addPayment(@NonNull UUID invoiceId, @NonNull PaymentRequest request);
}
