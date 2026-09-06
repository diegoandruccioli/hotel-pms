package com.hotelpms.billing.service;

import com.hotelpms.billing.dto.PaymentRequest;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.dto.PaymentSummaryResponse;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.lang.NonNull;

/**
 * Service interface for managing Payments.
 */
public interface PaymentService {

    /**
     * Adds a payment to a specific invoice.
     *
     * @param invoiceId the invoice UUID
     * @param request   the payment request
     * @return the recorded payment response
     */
    PaymentResponse addPayment(@NonNull UUID invoiceId, @NonNull PaymentRequest request);

    /**
     * Summarizes payments recorded on a single business date for the caller's
     * hotel, grouped by method — the night-audit cash-closing section.
     *
     * @param date the business date to summarize
     * @return the cash-closing summary
     */
    PaymentSummaryResponse getPaymentSummary(@NonNull LocalDate date);
}
