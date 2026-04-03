package com.hotelpms.billing.controller;

import com.hotelpms.billing.dto.PaymentRequest;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for managing Payments.
 */
@RestController
@RequestMapping("/api/v1/invoices/{invoiceId}/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Adds a generic payment to a specific invoice.
     * 
     * @param invoiceId the invoice UUID
     * @param request   the payment details
     * @return the created payment response
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> addPayment(@NonNull @PathVariable final UUID invoiceId,
            @NonNull @Valid @RequestBody final PaymentRequest request) {
        log.info("REST request to add a payment to invoice {}", invoiceId);
        final PaymentResponse response = paymentService.addPayment(invoiceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
