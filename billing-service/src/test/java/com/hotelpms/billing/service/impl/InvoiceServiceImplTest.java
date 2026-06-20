package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.ReservationClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.ReservationResponse;
import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceCharge;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.InvoiceRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.exception.BillingValidationException;
import com.hotelpms.billing.exception.InvoiceConflictException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.InvoiceChargeMapper;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceChargeRepository;
import com.hotelpms.billing.repository.InvoiceRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InvoiceServiceImpl using JUnit 5 and Mockito.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

        private static final BigDecimal TOTAL_AMOUNT = BigDecimal.valueOf(500);
        private static final String SIMPLE_DRINK = "Coffee";
        private static final String GUEST_FIRST_NAME = "John";
        private static final String GUEST_LAST_NAME = "Doe";
        private static final String GUEST_EMAIL = "john@example.com";

        @Mock
        private InvoiceRepository invoiceRepository;

        @Mock
        private InvoiceChargeRepository invoiceChargeRepository;

        @Mock
        private InvoiceMapper invoiceMapper;

        @Mock
        private InvoiceChargeMapper invoiceChargeMapper;

        @Mock
        private GuestClient guestClient;

        @Mock
        private ReservationClient reservationClient;

        @InjectMocks
        private InvoiceServiceImpl invoiceService;

        private UUID reservationId;
        private UUID guestId;
        private UUID hotelId;
        private InvoiceRequest request;

        @BeforeEach
        void setUp() {
                reservationId = UUID.randomUUID();
                guestId = UUID.randomUUID();
                hotelId = UUID.randomUUID();
                request = new InvoiceRequest(null, reservationId, guestId, TOTAL_AMOUNT, InvoiceStatus.ISSUED, null);

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

                final InvoiceResponse expectedResponse = new InvoiceResponse(invoice.getId(), hotelId, "INV-12345",
                                null, TOTAL_AMOUNT, InvoiceStatus.ISSUED, reservationId, guestId, null,
                                List.of(), List.of());

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
                assertEquals(hotelId, invoice.getHotelId());
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
        @DisplayName("Should successfully get existing invoice scoped to hotel")
        void shouldGetExistingInvoice() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                final InvoiceResponse expectedResponse = new InvoiceResponse(invoiceId, hotelId, "INV-123", null,
                                BigDecimal.TEN, InvoiceStatus.ISSUED, reservationId, guestId, null,
                                List.of(), List.of());

                when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId))
                                .thenReturn(Optional.of(invoice));
                when(invoiceMapper.toResponse(invoice)).thenReturn(expectedResponse);

                // Act
                final InvoiceResponse result = invoiceService.getInvoice(Objects.requireNonNull(invoiceId));

                // Assert
                assertNotNull(result);
                assertEquals(invoiceId, result.id());
        }

        @Test
        @DisplayName("Should throw NotFoundException for invoice belonging to a different hotel (IDOR-safe)")
        void shouldThrowWhenInvoiceBelongsToDifferentHotel() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                when(invoiceRepository.findByIdAndHotelId(Objects.requireNonNull(invoiceId), hotelId))
                                .thenReturn(Optional.empty());

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

        // ---------------------------------------------------------------
        // createInvoiceForStay
        // ---------------------------------------------------------------

        @Test
        @SuppressWarnings("null")
        @DisplayName("Should create invoice for stay with totalAmount=0 and status=ISSUED")
        void shouldCreateInvoiceForStaySuccessfully() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final StayInvoiceRequest stayRequest = new StayInvoiceRequest(stayId, guestId, reservationId);

                final Invoice savedInvoice = Invoice.builder()
                                .id(UUID.randomUUID())
                                .stayId(stayId)
                                .guestId(guestId)
                                .reservationId(reservationId)
                                .hotelId(hotelId)
                                .totalAmount(BigDecimal.ZERO)
                                .status(InvoiceStatus.ISSUED)
                                .issueDate(LocalDateTime.now())
                                .invoiceNumber("INV-ABCD1234")
                                .build();

                final InvoiceResponse expectedResponse = new InvoiceResponse(
                                savedInvoice.getId(), hotelId, "INV-ABCD1234", LocalDateTime.now(),
                                BigDecimal.ZERO, InvoiceStatus.ISSUED, reservationId, guestId, stayId,
                                List.of(), List.of());

                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
                when(invoiceRepository.save(notNull())).thenReturn(savedInvoice);
                when(invoiceMapper.toResponse(savedInvoice)).thenReturn(expectedResponse);

                // Act
                final InvoiceResponse result = invoiceService.createInvoiceForStay(stayRequest);

                // Assert
                assertNotNull(result);
                assertEquals(stayId, result.stayId());
                assertEquals(0, BigDecimal.ZERO.compareTo(result.totalAmount()));
                verify(invoiceRepository).save(notNull());
        }

        @Test
        @DisplayName("Should throw InvoiceConflictException when ISSUED invoice already exists for stay")
        void shouldThrowConflictWhenInvoiceAlreadyExistsForStay() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final StayInvoiceRequest stayRequest = new StayInvoiceRequest(stayId, guestId, reservationId);

                final Invoice existingInvoice = new Invoice();
                existingInvoice.setStatus(InvoiceStatus.ISSUED);

                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId))
                                .thenReturn(Optional.of(existingInvoice));

                // Act & Assert
                assertThrows(InvoiceConflictException.class,
                                () -> invoiceService.createInvoiceForStay(stayRequest));
        }

        // ---------------------------------------------------------------
        // addCharge
        // ---------------------------------------------------------------

        @Test
        @DisplayName("Should add FB_ORDER charge to open invoice and update totalAmount atomically")
        void shouldAddFbOrderChargeAndUpdateTotalAmount() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final UUID orderId = UUID.randomUUID();
                final BigDecimal chargeAmount = BigDecimal.valueOf(11);
                final ChargeRequest chargeRequest = new ChargeRequest(
                                ChargeType.FB_ORDER, "Espresso x2, Tiramisù x1", chargeAmount, orderId);

                final Invoice openInvoice = Invoice.builder()
                                .id(UUID.randomUUID())
                                .stayId(stayId)
                                .hotelId(hotelId)
                                .totalAmount(BigDecimal.ZERO)
                                .status(InvoiceStatus.ISSUED)
                                .invoiceNumber("INV-TESTXYZ0")
                                .build();

                final ChargeResponse expectedCharge = new ChargeResponse(
                                UUID.randomUUID(), openInvoice.getId(), ChargeType.FB_ORDER,
                                "Espresso x2, Tiramisù x1", chargeAmount, orderId, LocalDateTime.now());

                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId))
                                .thenReturn(Optional.of(openInvoice));
                when(invoiceRepository.save(openInvoice)).thenReturn(openInvoice);
                when(invoiceChargeRepository.save(any(InvoiceCharge.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(invoiceChargeMapper.toResponse(any(InvoiceCharge.class))).thenReturn(expectedCharge);

                // Act
                final ChargeResponse result = invoiceService.addCharge(Objects.requireNonNull(stayId), chargeRequest);

                // Assert
                assertNotNull(result);
                assertEquals(ChargeType.FB_ORDER, result.type());
                assertEquals(0, chargeAmount.compareTo(openInvoice.getTotalAmount()));
                verify(invoiceRepository).save(openInvoice);
        }

        @Test
        @DisplayName("Should throw NotFoundException when no invoice exists for stay (IDOR-safe lookup)")
        void shouldThrowNotFoundWhenNoInvoiceExistsForStay() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final ChargeRequest chargeRequest = new ChargeRequest(
                                ChargeType.FB_ORDER, SIMPLE_DRINK, BigDecimal.valueOf(3), null);

                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());

                // Act & Assert
                final Exception exception = assertThrows(NotFoundException.class,
                                () -> invoiceService.addCharge(Objects.requireNonNull(stayId), chargeRequest));
                assertEquals("INVOICE_NOT_FOUND_FOR_STAY", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw InvoiceConflictException when invoice is PAID (not open)")
        void shouldThrowConflictWhenInvoiceIsNotOpen() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final ChargeRequest chargeRequest = new ChargeRequest(
                                ChargeType.FB_ORDER, SIMPLE_DRINK, BigDecimal.valueOf(3), null);

                final Invoice paidInvoice = new Invoice();
                paidInvoice.setStatus(InvoiceStatus.PAID);
                paidInvoice.setTotalAmount(BigDecimal.TEN);

                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId))
                                .thenReturn(Optional.of(paidInvoice));

                // Act & Assert
                final Exception exception = assertThrows(InvoiceConflictException.class,
                                () -> invoiceService.addCharge(Objects.requireNonNull(stayId), chargeRequest));
                assertEquals("INVOICE_NOT_OPEN", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw NotFoundException for addCharge with stayId belonging to different hotel (IDOR)")
        void shouldThrowWhenStayBelongsToDifferentHotel() {
                // Arrange — stayId from another hotel; findByStayIdAndHotelId returns empty (hotel scoping)
                final UUID foreignStayId = UUID.randomUUID();
                final ChargeRequest chargeRequest = new ChargeRequest(
                                ChargeType.FB_ORDER, SIMPLE_DRINK, BigDecimal.valueOf(3), null);

                when(invoiceRepository.findByStayIdAndHotelId(foreignStayId, hotelId))
                                .thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(NotFoundException.class,
                                () -> invoiceService.addCharge(Objects.requireNonNull(foreignStayId), chargeRequest));
        }
}
