package com.hotelpms.billing.controller;

import com.hotelpms.billing.dto.PaymentSummaryResponse;
import com.hotelpms.billing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Read-only cash-closing summary, consumed by frontdesk-service's night
 * audit (internal Feign call, signed with role ADMIN by that service's
 * batch-job context) and by the owner-facing report UI. Split from {@link
 * InvoiceController}/{@link PaymentController} since it isn't scoped to a
 * single invoice.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentSummaryController {

    private static final String ROLE_ADMIN_OR_OWNER = "hasAnyRole('ADMIN', 'OWNER')";

    private final PaymentService paymentService;

    /**
     * Returns the payment totals by method for a single business date,
     * scoped to the caller's hotel — financial data, so ADMIN/OWNER only
     * (unlike frontdesk-service's day-sheet, which carries no figures).
     *
     * @param date the business date to summarize
     * @return the cash-closing summary
     */
    @GetMapping("/summary")
    @PreAuthorize(ROLE_ADMIN_OR_OWNER)
    public ResponseEntity<PaymentSummaryResponse> getPaymentSummary(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        log.info("REST request for payment summary on {}", date);
        return ResponseEntity.ok(paymentService.getPaymentSummary(date));
    }
}
