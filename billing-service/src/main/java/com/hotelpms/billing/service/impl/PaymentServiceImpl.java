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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
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
        log.info("Attempting to add payment of {} to invoice {}", request.amount(), invoiceId);

        final Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND"));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BillingValidationException("INVOICE_ALREADY_PAID");
        }

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BillingValidationException("INVOICE_CANCELLED");
        }

        final BigDecimal currentTotalPaid = invoice.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final BigDecimal balanceDue = invoice.getTotalAmount().subtract(currentTotalPaid);

        if (request.amount().compareTo(balanceDue) > 0) {
            throw new BillingValidationException("PAYMENT_EXCEEDS_BALANCE");
        }

        final Payment payment = paymentMapper.toEntity(request);
        payment.setPaymentDate(LocalDateTime.now());

        invoice.addPayment(payment);
        final Payment savedPayment = paymentRepository.save(payment);

        // Check if fully paid
        final BigDecimal newTotalPaid = currentTotalPaid.add(request.amount());
        if (newTotalPaid.compareTo(invoice.getTotalAmount()) == 0) {
            log.info("Invoice {} is now fully paid. Updating status.", invoiceId);
            invoice.setStatus(InvoiceStatus.PAID);
        }

        invoiceRepository.save(invoice); // Save cascaded payment addition and potential status change

        return paymentMapper.toResponse(savedPayment);
    }
}
