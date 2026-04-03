package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.Payment;
import com.hotelpms.billing.domain.PaymentMethod;
import com.hotelpms.billing.dto.PaymentRequest;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.exception.BillingValidationException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.PaymentMapper;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PaymentServiceImpl using JUnit 5 and Mockito.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final String TXN_ID = "TXN123";
    private static final BigDecimal AMOUNT_100 = BigDecimal.valueOf(100);
    private static final BigDecimal AMOUNT_500 = BigDecimal.valueOf(500);
    private static final BigDecimal AMOUNT_600 = BigDecimal.valueOf(600);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID invoiceId;
    private PaymentRequest request;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoiceId = UUID.randomUUID();
        request = new PaymentRequest(AMOUNT_100, PaymentMethod.CREDIT_CARD, TXN_ID);

        invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setTotalAmount(AMOUNT_500);
        invoice.setStatus(InvoiceStatus.ISSUED);
    }

    @Test
    @DisplayName("Should successfully add payment and NOT change invoice to PAID if balance remains")
    void shouldAddPaymentSuccessfully() {
        // Arrange
        final Payment payment = new Payment();
        payment.setAmount(AMOUNT_100);

        final PaymentResponse response = new PaymentResponse(UUID.randomUUID(), LocalDateTime.now(), AMOUNT_100,
                PaymentMethod.CREDIT_CARD, TXN_ID, invoiceId);

        when(invoiceRepository.findById(Objects.requireNonNull(invoiceId))).thenReturn(Optional.of(invoice));
        when(paymentMapper.toEntity(Objects.requireNonNull(request))).thenReturn(payment);
        when(paymentRepository.save(Objects.requireNonNull(payment))).thenReturn(payment);
        when(paymentMapper.toResponse(Objects.requireNonNull(payment))).thenReturn(response);

        // Act
        final PaymentResponse result = paymentService.addPayment(Objects.requireNonNull(invoiceId),
                Objects.requireNonNull(request));

        // Assert
        assertEquals(response.amount(), result.amount());
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        verify(invoiceRepository).save(Objects.requireNonNull(invoice));
    }

    @Test
    @DisplayName("Should successfully add payment and change invoice to PAID if balance is zero")
    void shouldAddPaymentAndMarkAsPaid() {
        // Arrange
        request = new PaymentRequest(AMOUNT_500, PaymentMethod.CREDIT_CARD, TXN_ID);
        final Payment payment = new Payment();
        payment.setAmount(AMOUNT_500);

        final PaymentResponse response = new PaymentResponse(UUID.randomUUID(), LocalDateTime.now(), AMOUNT_500,
                PaymentMethod.CREDIT_CARD, TXN_ID, invoiceId);

        when(invoiceRepository.findById(Objects.requireNonNull(invoiceId))).thenReturn(Optional.of(invoice));
        when(paymentMapper.toEntity(Objects.requireNonNull(request))).thenReturn(payment);
        when(paymentRepository.save(Objects.requireNonNull(payment))).thenReturn(payment);
        when(paymentMapper.toResponse(Objects.requireNonNull(payment))).thenReturn(response);

        // Act
        paymentService.addPayment(Objects.requireNonNull(invoiceId), Objects.requireNonNull(request));

        // Assert
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        verify(invoiceRepository).save(Objects.requireNonNull(invoice));
    }

    @Test
    @DisplayName("Should throw BillingValidationException when paying more than balance due")
    void shouldThrowWhenOverpaying() {
        // Arrange
        request = new PaymentRequest(AMOUNT_600, PaymentMethod.CREDIT_CARD, TXN_ID);
        when(invoiceRepository.findById(Objects.requireNonNull(invoiceId))).thenReturn(Optional.of(invoice));

        // Act & Assert
        final Exception exception = assertThrows(BillingValidationException.class,
                () -> paymentService.addPayment(Objects.requireNonNull(invoiceId), Objects.requireNonNull(request)));
        assertEquals("PAYMENT_EXCEEDS_BALANCE", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw BillingValidationException when invoice is cancelled")
    void shouldThrowWhenInvoiceCancelled() {
        // Arrange
        invoice.setStatus(InvoiceStatus.CANCELLED);
        when(invoiceRepository.findById(Objects.requireNonNull(invoiceId))).thenReturn(Optional.of(invoice));

        // Act & Assert
        assertThrows(BillingValidationException.class,
                () -> paymentService.addPayment(Objects.requireNonNull(invoiceId), Objects.requireNonNull(request)));
    }

    @Test
    @DisplayName("Should throw NotFoundException when invoice does not exist")
    void shouldThrowWhenInvoiceNotFound() {
        // Arrange
        when(invoiceRepository.findById(Objects.requireNonNull(invoiceId))).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> paymentService.addPayment(Objects.requireNonNull(invoiceId), Objects.requireNonNull(request)));
    }
}
