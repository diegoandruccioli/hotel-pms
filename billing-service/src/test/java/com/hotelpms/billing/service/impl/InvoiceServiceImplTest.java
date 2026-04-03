package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.ReservationClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.ReservationResponse;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.InvoiceRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.exception.BillingValidationException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InvoiceServiceImpl using JUnit 5 and Mockito.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

        private static final BigDecimal TOTAL_AMOUNT = BigDecimal.valueOf(500);
        private static final String GUEST_FIRST_NAME = "John";
        private static final String GUEST_LAST_NAME = "Doe";
        private static final String GUEST_EMAIL = "john@example.com";

        @Mock
        private InvoiceRepository invoiceRepository;

        @Mock
        private InvoiceMapper invoiceMapper;

        @Mock
        private GuestClient guestClient;

        @Mock
        private ReservationClient reservationClient;

        @InjectMocks
        private InvoiceServiceImpl invoiceService;

        private UUID reservationId;
        private UUID guestId;
        private InvoiceRequest request;

        @BeforeEach
        void setUp() {
                reservationId = UUID.randomUUID();
                guestId = UUID.randomUUID();
                request = new InvoiceRequest(null, reservationId, guestId, TOTAL_AMOUNT, InvoiceStatus.ISSUED);
        }

        @Test
        @DisplayName("Should successfully create an invoice when all validation passes")
        void shouldCreateInvoiceSuccessfully() {
                // Arrange
                final GuestResponse guestResponse = new GuestResponse(guestId, GUEST_FIRST_NAME, GUEST_LAST_NAME,
                                GUEST_EMAIL);
                final ReservationResponse resResponse = new ReservationResponse(reservationId, guestId,
                                UUID.randomUUID(),
                                LocalDate.now(), LocalDate.now().plusDays(2), TOTAL_AMOUNT, "CHECKED_IN");

                final Invoice invoice = new Invoice();
                invoice.setId(UUID.randomUUID());
                invoice.setTotalAmount(TOTAL_AMOUNT);

                final InvoiceResponse expectedResponse = new InvoiceResponse(invoice.getId(), null, "INV-12345", null,
                                TOTAL_AMOUNT, InvoiceStatus.ISSUED, reservationId, guestId, List.of());

                when(guestClient.getGuestById(guestId)).thenReturn(guestResponse);
                when(reservationClient.getReservationById(reservationId)).thenReturn(resResponse);
                when(invoiceMapper.toEntity(Objects.requireNonNull(request))).thenReturn(invoice);
                when(invoiceRepository.save(invoice)).thenReturn(invoice);
                when(invoiceMapper.toResponse(invoice)).thenReturn(expectedResponse);

                // Act
                final InvoiceResponse result = invoiceService.createInvoice(Objects.requireNonNull(request));

                // Assert
                assertNotNull(result);
                assertEquals(expectedResponse.id(), result.id());
                verify(invoiceRepository).save(invoice);
        }

        @Test
        @DisplayName("Should throw BillingValidationException when guest is invalid")
        void shouldThrowWhenGuestIsInvalid() {
                // Arrange
                when(guestClient.getGuestById(guestId)).thenReturn(null);

                // Act & Assert
                final Exception exception = assertThrows(BillingValidationException.class,
                                () -> invoiceService.createInvoice(Objects.requireNonNull(request)));
                assertEquals("INVALID_GUEST_DETAILS", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw BillingValidationException when reservation owner does not match guest")
        void shouldThrowWhenOwnerMismatch() {
                // Arrange
                final GuestResponse guestResponse = new GuestResponse(guestId, GUEST_FIRST_NAME, GUEST_LAST_NAME,
                                GUEST_EMAIL);
                final ReservationResponse resResponse = new ReservationResponse(reservationId, UUID.randomUUID(),
                                UUID.randomUUID(),
                                LocalDate.now(), LocalDate.now().plusDays(2), TOTAL_AMOUNT, "CHECKED_IN");

                when(guestClient.getGuestById(guestId)).thenReturn(guestResponse);
                when(reservationClient.getReservationById(reservationId)).thenReturn(resResponse);

                // Act & Assert
                final Exception exception = assertThrows(BillingValidationException.class,
                                () -> invoiceService.createInvoice(Objects.requireNonNull(request)));
                assertEquals("GUEST_MISMATCH", exception.getMessage());
        }

        @Test
        @DisplayName("Should successfully get existing invoice")
        void shouldGetExistingInvoice() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                final InvoiceResponse expectedResponse = new InvoiceResponse(invoiceId, null, "INV-123", null,
                                BigDecimal.TEN,
                                InvoiceStatus.ISSUED, reservationId, guestId, List.of());

                when(invoiceRepository.findById(Objects.requireNonNull(invoiceId))).thenReturn(Optional.of(invoice));
                when(invoiceMapper.toResponse(invoice)).thenReturn(expectedResponse);

                // Act
                final InvoiceResponse result = invoiceService.getInvoice(Objects.requireNonNull(invoiceId));

                // Assert
                assertNotNull(result);
                assertEquals(invoiceId, result.id());
        }

        @Test
        @DisplayName("Should throw NotFoundException for non-existent invoice")
        void shouldThrowWhenGettingNonExistentInvoice() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                when(invoiceRepository.findById(Objects.requireNonNull(invoiceId))).thenReturn(Optional.empty());

                // Act & Assert
                final Exception exception = assertThrows(NotFoundException.class,
                                () -> invoiceService.getInvoice(Objects.requireNonNull(invoiceId)));
                assertEquals("INVOICE_NOT_FOUND", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw RuntimeException when external reservation service is down")
        void shouldThrowWhenReservationServiceIsDown() {
                // Arrange
                final GuestResponse guestResponse = new GuestResponse(guestId, GUEST_FIRST_NAME, GUEST_LAST_NAME,
                                GUEST_EMAIL);
                when(guestClient.getGuestById(guestId)).thenReturn(guestResponse);
                when(reservationClient.getReservationById(reservationId))
                                .thenThrow(new RuntimeException("Service Unavailable"));

                final Exception exception = assertThrows(RuntimeException.class,
                                () -> invoiceService.createInvoice(Objects.requireNonNull(request)));
                assertEquals("Service Unavailable", exception.getMessage());
        }
}
