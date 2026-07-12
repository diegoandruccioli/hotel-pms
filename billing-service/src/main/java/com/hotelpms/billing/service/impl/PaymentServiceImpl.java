package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.Payment;
import com.hotelpms.billing.dto.PaymentRequest;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.exception.BillingValidationException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.PaymentMapper;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.repository.PaymentRepository;
import com.hotelpms.billing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementation of the PaymentService interface.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMapper paymentMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PaymentResponse addPayment(@NonNull final UUID invoiceId, @NonNull final PaymentRequest request) {
        final UUID hotelId = resolveHotelId();
        final Invoice invoice = invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND"));

        final BigDecimal paymentAmount = request.amount().setScale(2, RoundingMode.HALF_UP);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            log.warn("[BILLING] PAYMENT_REJECTED | invoiceId={} | hotelId={} | reason=INVOICE_ALREADY_PAID",
                    invoiceId, hotelId);
            throw new BillingValidationException("INVOICE_ALREADY_PAID");
        }

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            log.warn("[BILLING] PAYMENT_REJECTED | invoiceId={} | hotelId={} | reason=INVOICE_CANCELLED",
                    invoiceId, hotelId);
            throw new BillingValidationException("INVOICE_CANCELLED");
        }

        final BigDecimal currentTotalPaid = invoice.getPayments().stream()
                .map((@NonNull Payment p) -> p.getAmount())
                .reduce(BigDecimal.ZERO, (@NonNull BigDecimal a, @NonNull BigDecimal b) -> a.add(b));

        final BigDecimal balanceDue = invoice.getTotalAmount().subtract(currentTotalPaid);

        if (paymentAmount.compareTo(balanceDue) > 0) {
            log.warn("[BILLING] PAYMENT_REJECTED | invoiceId={} | hotelId={} | reason=PAYMENT_EXCEEDS_BALANCE"
                    + " | amount={} | balanceDue={}",
                    invoiceId, hotelId, paymentAmount, balanceDue);
            throw new BillingValidationException("PAYMENT_EXCEEDS_BALANCE");
        }

        final Payment payment = paymentMapper.toEntity(request);
        payment.setAmount(paymentAmount);
        payment.setPaymentDate(LocalDateTime.now());

        invoice.addPayment(payment);
        final Payment savedPayment = paymentRepository.save(payment);

        log.info("[BILLING] PAYMENT_ADDED | invoiceId={} | paymentId={} | amount={} | method={} | hotelId={}",
                invoiceId, savedPayment.getId(), paymentAmount, request.paymentMethod(), hotelId);

        // Check if fully paid
        final BigDecimal newTotalPaid = currentTotalPaid.add(paymentAmount);
        if (newTotalPaid.compareTo(invoice.getTotalAmount()) == 0) {
            log.info("[BILLING] INVOICE_PAID | invoiceId={} | totalAmount={} | hotelId={}",
                    invoiceId, invoice.getTotalAmount(), hotelId);
            invoice.setStatus(InvoiceStatus.PAID);
        }

        invoiceRepository.save(invoice); // Save cascaded payment addition and potential status change

        return paymentMapper.toResponse(savedPayment);
    }

    /**
     * Extracts the hotel UUID from the current authentication context.
     * The hotel ID is stored as {@code details} by {@link com.hotelpms.billing.security.InternalAuthFilter}
     * after reading the {@code X-Auth-Hotel} header injected by the API Gateway.
     *
     * @return the hotel UUID of the authenticated caller
     * @throws IllegalStateException if the security context is missing or malformed
     */
    private UUID resolveHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr)) {
            throw new IllegalStateException("MISSING_HOTEL_CONTEXT");
        }
        return UUID.fromString(hotelIdStr);
    }
}
