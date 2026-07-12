package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceCharge;
import com.hotelpms.billing.domain.InvoiceSequence;
import com.hotelpms.billing.domain.InvoiceSequenceId;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.exception.InvoiceConflictException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.InvoiceChargeMapper;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceChargeRepository;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.repository.InvoiceSequenceRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InvoiceServiceImpl using JUnit 5 and Mockito.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class InvoiceServiceImplTest {

        private static final String SIMPLE_DRINK = "Coffee";

        @Mock
        private InvoiceRepository invoiceRepository;

        @Mock
        private InvoiceChargeRepository invoiceChargeRepository;

        @Mock
        private InvoiceSequenceRepository sequenceRepository;

        @Mock
        private InvoiceMapper invoiceMapper;

        @Mock
        private InvoiceChargeMapper invoiceChargeMapper;

        @InjectMocks
        private InvoiceServiceImpl invoiceService;

        private UUID reservationId;
        private UUID guestId;
        private UUID hotelId;

        @BeforeEach
        void setUp() {
                reservationId = UUID.randomUUID();
                guestId = UUID.randomUUID();
                hotelId = UUID.randomUUID();

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

        // ---------------------------------------------------------------
        // createInvoiceForStay
        // ---------------------------------------------------------------

        @Test
        @DisplayName("Should create invoice for stay with totalAmount=0, status=ISSUED and sequential number")
        void shouldCreateInvoiceForStaySuccessfully() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final StayInvoiceRequest stayRequest = new StayInvoiceRequest(stayId, guestId, reservationId);

                final String expectedYear = String.valueOf(LocalDate.now().getYear());

                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
                when(sequenceRepository.findByHotelIdAndYearForUpdate(eq(hotelId), anyInt()))
                                .thenReturn(Optional.empty());
                when(invoiceRepository.save(notNull())).thenAnswer(inv -> inv.getArgument(0));
                when(invoiceMapper.toResponse(any(Invoice.class))).thenAnswer(inv -> {
                        final Invoice i = inv.getArgument(0);
                        return new InvoiceResponse(i.getId(), hotelId, i.getInvoiceNumber(),
                                        LocalDateTime.now(), BigDecimal.ZERO, InvoiceStatus.ISSUED,
                                        reservationId, guestId, stayId, List.of(), List.of());
                });

                // Act
                final InvoiceResponse result = invoiceService.createInvoiceForStay(stayRequest);

                // Assert — formato YYYY/NNNN e primo numero della sequenza
                assertNotNull(result);
                assertEquals(stayId, result.stayId());
                assertEquals(0, BigDecimal.ZERO.compareTo(result.totalAmount()));
                assertTrue(result.invoiceNumber().matches("\\d{4}/\\d{4}"),
                                "Invoice number must match YYYY/NNNN format");
                assertTrue(result.invoiceNumber().startsWith(expectedYear + "/"),
                                "Invoice number must start with current year");
                assertEquals(expectedYear + "/0001", result.invoiceNumber());
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
                                "Espresso x2, Tiramisù x1", chargeAmount, new BigDecimal("0.10"),
                                orderId, LocalDateTime.now());

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

        // ---------------------------------------------------------------
        // generateInvoiceNumber — numerazione progressiva
        // ---------------------------------------------------------------

        @Test
        @DisplayName("Should generate YYYY/0001 when no sequence exists for hotel+year (first invoice)")
        void shouldGenerateFirstInvoiceNumberWhenNoSequenceExists() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final StayInvoiceRequest request = new StayInvoiceRequest(stayId, guestId, reservationId);
                final int currentYear = LocalDate.now().getYear();

                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
                when(sequenceRepository.findByHotelIdAndYearForUpdate(eq(hotelId), eq(currentYear)))
                                .thenReturn(Optional.empty());
                when(invoiceRepository.save(notNull())).thenAnswer(inv -> inv.getArgument(0));
                when(invoiceMapper.toResponse(any(Invoice.class))).thenAnswer(inv -> {
                        final Invoice i = inv.getArgument(0);
                        return new InvoiceResponse(i.getId(), hotelId, i.getInvoiceNumber(),
                                        LocalDateTime.now(), BigDecimal.ZERO, InvoiceStatus.ISSUED,
                                        reservationId, guestId, stayId, List.of(), List.of());
                });

                // Act
                final InvoiceResponse result = invoiceService.createInvoiceForStay(request);

                // Assert — primo numero dell'anno
                assertEquals(currentYear + "/0001", result.invoiceNumber());

                // Verifica che la sequenza sia stata salvata con lastSeq=1
                final ArgumentCaptor<InvoiceSequence> seqCaptor =
                                ArgumentCaptor.forClass(InvoiceSequence.class);
                verify(sequenceRepository).save(seqCaptor.capture());
                assertEquals(1L, seqCaptor.getValue().getLastSeq());
                assertEquals(hotelId, seqCaptor.getValue().getId().getHotelId());
                assertEquals(currentYear, seqCaptor.getValue().getId().getYear());
        }

        // ---------------------------------------------------------------
        // vatRateFor — aliquote IVA per tipo addebito (E12)
        // ---------------------------------------------------------------

        @Test
        @DisplayName("Should set vatRate=0.10 for FB_ORDER charge")
        void shouldSetVatRateOnFbOrderCharge() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final BigDecimal chargeAmount = BigDecimal.valueOf(11);
                final ChargeRequest chargeRequest = new ChargeRequest(
                                ChargeType.FB_ORDER, SIMPLE_DRINK, chargeAmount, null);
                final Invoice openInvoice = Invoice.builder()
                                .id(UUID.randomUUID()).stayId(stayId).hotelId(hotelId)
                                .totalAmount(BigDecimal.ZERO).status(InvoiceStatus.ISSUED)
                                .invoiceNumber("INV-TEST").build();
                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId))
                                .thenReturn(Optional.of(openInvoice));
                when(invoiceRepository.save(openInvoice)).thenReturn(openInvoice);
                when(invoiceChargeRepository.save(any(InvoiceCharge.class)))
                                .thenAnswer(inv -> inv.getArgument(0));
                when(invoiceChargeMapper.toResponse(any(InvoiceCharge.class)))
                                .thenAnswer(inv -> {
                                        final InvoiceCharge c = inv.getArgument(0);
                                        return new ChargeResponse(UUID.randomUUID(), openInvoice.getId(),
                                                        c.getType(), c.getDescription(), c.getAmount(),
                                                        c.getVatRate(), c.getReferenceId(), null);
                                });

                // Act
                invoiceService.addCharge(Objects.requireNonNull(stayId), chargeRequest);

                // Assert — vatRate 10% salvato sulla riga
                final ArgumentCaptor<InvoiceCharge> captor = ArgumentCaptor.forClass(InvoiceCharge.class);
                verify(invoiceChargeRepository).save(captor.capture());
                assertEquals(0, new BigDecimal("0.10").compareTo(captor.getValue().getVatRate()));
        }

        @Test
        @DisplayName("Should set vatRate=0.22 for EXTRA charge")
        void shouldSetHigherVatRateForExtraCharge() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final ChargeRequest chargeRequest = new ChargeRequest(
                                ChargeType.EXTRA, "Minibar", BigDecimal.TEN, null);
                final Invoice openInvoice = Invoice.builder()
                                .id(UUID.randomUUID()).stayId(stayId).hotelId(hotelId)
                                .totalAmount(BigDecimal.ZERO).status(InvoiceStatus.ISSUED)
                                .invoiceNumber("INV-TEST").build();
                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId))
                                .thenReturn(Optional.of(openInvoice));
                when(invoiceRepository.save(openInvoice)).thenReturn(openInvoice);
                when(invoiceChargeRepository.save(any(InvoiceCharge.class)))
                                .thenAnswer(inv -> inv.getArgument(0));
                when(invoiceChargeMapper.toResponse(any(InvoiceCharge.class)))
                                .thenAnswer(inv -> {
                                        final InvoiceCharge c = inv.getArgument(0);
                                        return new ChargeResponse(UUID.randomUUID(), openInvoice.getId(),
                                                        c.getType(), c.getDescription(), c.getAmount(),
                                                        c.getVatRate(), c.getReferenceId(), null);
                                });

                // Act
                invoiceService.addCharge(Objects.requireNonNull(stayId), chargeRequest);

                // Assert — vatRate 22% per EXTRA
                final ArgumentCaptor<InvoiceCharge> captor = ArgumentCaptor.forClass(InvoiceCharge.class);
                verify(invoiceChargeRepository).save(captor.capture());
                assertEquals(0, new BigDecimal("0.22").compareTo(captor.getValue().getVatRate()));
        }

        @Test
        @DisplayName("Should produce YYYY/0006 when existing sequence has lastSeq=5")
        void shouldIncrementExistingSequence() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final StayInvoiceRequest request = new StayInvoiceRequest(stayId, guestId, reservationId);
                final int currentYear = LocalDate.now().getYear();

                final long seqBefore = 5L;
                final InvoiceSequence existingSeq = InvoiceSequence.builder()
                                .id(new InvoiceSequenceId(hotelId, currentYear))
                                .lastSeq(seqBefore)
                                .build();

                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
                when(sequenceRepository.findByHotelIdAndYearForUpdate(eq(hotelId), eq(currentYear)))
                                .thenReturn(Optional.of(existingSeq));
                when(invoiceRepository.save(notNull())).thenAnswer(inv -> inv.getArgument(0));
                when(invoiceMapper.toResponse(any(Invoice.class))).thenAnswer(inv -> {
                        final Invoice i = inv.getArgument(0);
                        return new InvoiceResponse(i.getId(), hotelId, i.getInvoiceNumber(),
                                        LocalDateTime.now(), BigDecimal.ZERO, InvoiceStatus.ISSUED,
                                        reservationId, guestId, stayId, List.of(), List.of());
                });

                // Act
                final InvoiceResponse result = invoiceService.createInvoiceForStay(request);

                // Assert — sequenza incrementata da seqBefore a seqBefore+1
                assertEquals(currentYear + "/000" + (seqBefore + 1), result.invoiceNumber());
                assertEquals(seqBefore + 1, existingSeq.getLastSeq());
        }
}
