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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private UUID hotelId;
    private PaymentRequest request;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoiceId = UUID.randomUUID();
        hotelId = UUID.randomUUID();
        request = new PaymentRequest(AMOUNT_100, PaymentMethod.CREDIT_CARD, TXN_ID);

        invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setTotalAmount(AMOUNT_500);
        invoice.setStatus(InvoiceStatus.ISSUED);

        final UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("admin", "", List.of());
        auth.setDetails(hotelId.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should successfully add payment and NOT change invoice to PAID if balance remains")
    void shouldAddPaymentSuccessfully() {
        // Arrange
        final Payment payment = new Payment();
        payment.setAmount(AMOUNT_100);

        final PaymentResponse response = new PaymentResponse(UUID.randomUUID(), LocalDateTime.now(), AMOUNT_100,
                PaymentMethod.CREDIT_CARD, TXN_ID, invoiceId);

        when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId)).thenReturn(Optional.of(invoice));
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

        when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId)).thenReturn(Optional.of(invoice));
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
        when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId)).thenReturn(Optional.of(invoice));

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
        when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId)).thenReturn(Optional.of(invoice));

        // Act & Assert
        assertThrows(BillingValidationException.class,
                () -> paymentService.addPayment(Objects.requireNonNull(invoiceId), Objects.requireNonNull(request)));
    }

    @Test
    @DisplayName("Should throw NotFoundException when invoice does not exist")
    void shouldThrowWhenInvoiceNotFound() {
        // Arrange
        when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> paymentService.addPayment(Objects.requireNonNull(invoiceId), Objects.requireNonNull(request)));
    }

    @Test
    @DisplayName("Should throw NotFoundException when invoice belongs to a different hotel (IDOR-safe)")
    void shouldThrowWhenInvoiceBelongsToDifferentHotel() {
        // Arrange — findByIdAndHotelId returns empty because hotelId doesn't match
        when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> paymentService.addPayment(Objects.requireNonNull(invoiceId), Objects.requireNonNull(request)));
    }

    @Test
    @DisplayName("Should throw BillingValidationException when invoice is already paid (T-BILL-02)")
    void shouldThrowWhenInvoiceAlreadyPaid() {
        // Arrange
        invoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId))
                .thenReturn(Optional.of(invoice));

        // Act & Assert
        final Exception exception = assertThrows(BillingValidationException.class,
                () -> paymentService.addPayment(Objects.requireNonNull(invoiceId), Objects.requireNonNull(request)));
        assertEquals("INVOICE_ALREADY_PAID", exception.getMessage());
    }

    @Test
    @DisplayName("Should normalize payment amount to 2 decimal places before storing (T-BILL-02)")
    void shouldNormalizeAmountToTwoDecimalPlaces() {
        // Arrange: amount with 3 decimal places (100.005 rounds to 100.01 with HALF_UP)
        final BigDecimal rawAmount = new BigDecimal("100.005");
        final BigDecimal expectedNormalized = new BigDecimal("100.01");
        request = new PaymentRequest(rawAmount, PaymentMethod.CREDIT_CARD, TXN_ID);

        final Payment payment = new Payment();
        final PaymentResponse response = new PaymentResponse(UUID.randomUUID(), LocalDateTime.now(),
                expectedNormalized, PaymentMethod.CREDIT_CARD, TXN_ID, invoiceId);

        when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId))
                .thenReturn(Optional.of(invoice));
        when(paymentMapper.toEntity(Objects.requireNonNull(request))).thenReturn(payment);
        when(paymentRepository.save(Objects.requireNonNull(payment))).thenReturn(payment);
        when(paymentMapper.toResponse(Objects.requireNonNull(payment))).thenReturn(response);

        // Act
        paymentService.addPayment(Objects.requireNonNull(invoiceId), Objects.requireNonNull(request));

        // Assert: the payment entity amount is normalized to 2 decimal places
        assertEquals(0, expectedNormalized.compareTo(Objects.requireNonNull(payment.getAmount())));
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus()); // 100.01 < 500, not fully paid
    }
}
