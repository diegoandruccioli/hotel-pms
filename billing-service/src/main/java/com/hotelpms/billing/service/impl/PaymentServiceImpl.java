package com.hotelpms.billing.service.impl;

import com.hotelpms.internalauth.security.TenantContext;

import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.Payment;
import com.hotelpms.billing.dto.PaymentMethodTotalResponse;
import com.hotelpms.billing.dto.PaymentRequest;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.dto.PaymentSummaryResponse;
import com.hotelpms.billing.exception.BillingValidationException;
import com.hotelpms.billing.exception.InvoiceConflictException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.PaymentMapper;
import com.hotelpms.billing.repository.InvoiceFiscalExportRepository;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.repository.PaymentMethodTotal;
import com.hotelpms.billing.repository.PaymentRepository;
import com.hotelpms.billing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    private final InvoiceFiscalExportRepository invoiceFiscalExportRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PaymentResponse addPayment(@NonNull final UUID invoiceId, @NonNull final PaymentRequest request) {
        final UUID hotelId = TenantContext.resolveHotelId();
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

        // A payment changes the invoice's PAID/ISSUED status, a fiscally-relevant field
        // once a FatturaPA export exists — same guard as addCharge/updateDocumentType in
        // InvoiceServiceImpl, kept here as its own check because PaymentServiceImpl is a
        // separate class with its own Invoice lookup (T-BILL-06/round 2 bug #3).
        if (invoiceFiscalExportRepository.existsByInvoiceId(invoice.getId())) {
            log.warn("[BILLING] PAYMENT_REJECTED | invoiceId={} | hotelId={} | reason=INVOICE_LOCKED_AFTER_EXPORT",
                    invoiceId, hotelId);
            throw new InvoiceConflictException("INVOICE_LOCKED_AFTER_EXPORT");
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

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PaymentSummaryResponse getPaymentSummary(@NonNull final LocalDate date) {
        final UUID hotelId = TenantContext.resolveHotelId();
        // Half-open [date 00:00, date+1 00:00) window on paymentDate — a plain
        // DATE(payment_date) = :date comparison would need a native query and
        // wouldn't use an index on the timestamp column the way a range does.
        final LocalDateTime from = date.atStartOfDay();
        final LocalDateTime to = date.plusDays(1).atStartOfDay();
        final List<PaymentMethodTotal> totals =
                paymentRepository.sumByHotelIdAndPaymentDateBetweenGroupedByMethod(hotelId, from, to);

        final List<PaymentMethodTotalResponse> byMethod = totals.stream()
                .map((@NonNull PaymentMethodTotal t) -> new PaymentMethodTotalResponse(t.getPaymentMethod(), t.getTotal()))
                .toList();
        final BigDecimal grandTotal = byMethod.stream()
                .map(PaymentMethodTotalResponse::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PaymentSummaryResponse(date, byMethod, grandTotal);
    }

}
