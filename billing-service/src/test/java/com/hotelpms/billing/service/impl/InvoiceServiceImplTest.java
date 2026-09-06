package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.GuestSearchPageResponse;
import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.FolioType;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.SdiStatus;
import com.hotelpms.billing.domain.InvoiceCharge;
import com.hotelpms.billing.domain.InvoiceSequence;
import com.hotelpms.billing.domain.InvoiceSequenceId;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.GroupChargeRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.InvoiceSearchResultResponse;
import com.hotelpms.billing.dto.MasterFolioRequest;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.exception.InvoiceConflictException;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.mapper.InvoiceChargeMapper;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceChargeRepository;
import com.hotelpms.billing.repository.InvoiceFiscalExportRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InvoiceServiceImpl using JUnit 5 and Mockito.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class InvoiceServiceImplTest {

        private static final String SIMPLE_DRINK = "Coffee";
        private static final String INV_123 = "INV-123";
        private static final String QUERY_MARIO = "mario";
        private static final String GUEST_FIRST_NAME_MARIO = "Mario";
        private static final String SORT_FIELD_ISSUE_DATE = "issueDate";
        private static final int GUEST_SEARCH_CAP = 200;
        private static final int PAGE_ZERO = 0;
        private static final int PAGE_SIZE_TWENTY = 20;
        private static final int SEARCH_YEAR = 2026;
        private static final int SEARCH_MONTH = 8;
        private static final int DAY_ONE = 1;
        private static final int DAY_FIVE = 5;
        private static final int EXPORT_PAGE_SIZE = 500;

        @Mock
        private InvoiceRepository invoiceRepository;

        @Mock
        private InvoiceChargeRepository invoiceChargeRepository;

        @Mock
        private InvoiceSequenceRepository sequenceRepository;

        // Unstubbed boolean-returning mock methods default to false (Mockito), i.e.
        // "not fiscally exported yet" — matches the pre-export test fixtures below.
        @Mock
        private InvoiceFiscalExportRepository invoiceFiscalExportRepository;

        @Mock
        private InvoiceMapper invoiceMapper;

        @Mock
        private InvoiceChargeMapper invoiceChargeMapper;

        @Mock
        private GuestClient guestClient;

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
                final InvoiceResponse expectedResponse = new InvoiceResponse(invoiceId, hotelId, INV_123, null,
                                BigDecimal.TEN, InvoiceStatus.ISSUED, reservationId, guestId, null,
                                null, null, List.of(), List.of());

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
                                        reservationId, guestId, stayId, null, null, List.of(), List.of());
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
                                ChargeType.FB_ORDER, "Espresso x2, Tiramisù x1", chargeAmount, orderId, null, null);

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
                                "Espresso x2, Tiramisù x1", chargeAmount, new BigDecimal("0.10"), null,
                                orderId, null, null, LocalDateTime.now());

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
                                ChargeType.FB_ORDER, SIMPLE_DRINK, BigDecimal.valueOf(3), null, null, null);

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
                                ChargeType.FB_ORDER, SIMPLE_DRINK, BigDecimal.valueOf(3), null, null, null);

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
        @DisplayName("Should throw InvoiceConflictException when invoice was already fiscally exported")
        void shouldThrowLockedWhenInvoiceAlreadyExported() {
                // Arrange
                final UUID stayId = UUID.randomUUID();
                final ChargeRequest chargeRequest = new ChargeRequest(
                                ChargeType.FB_ORDER, SIMPLE_DRINK, BigDecimal.valueOf(3), null, null, null);

                final Invoice exportedInvoice = Invoice.builder()
                                .id(UUID.randomUUID())
                                .stayId(stayId)
                                .hotelId(hotelId)
                                .totalAmount(BigDecimal.TEN)
                                .status(InvoiceStatus.ISSUED)
                                .invoiceNumber("INV-TESTXYZ1")
                                .build();

                when(invoiceRepository.findByStayIdAndHotelId(stayId, hotelId))
                                .thenReturn(Optional.of(exportedInvoice));
                when(invoiceFiscalExportRepository.existsByInvoiceId(exportedInvoice.getId()))
                                .thenReturn(true);

                // Act & Assert
                final Exception exception = assertThrows(InvoiceConflictException.class,
                                () -> invoiceService.addCharge(Objects.requireNonNull(stayId), chargeRequest));
                assertEquals("INVOICE_LOCKED_AFTER_EXPORT", exception.getMessage());
                verify(invoiceChargeRepository, never()).save(any(InvoiceCharge.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException for addCharge with stayId belonging to different hotel (IDOR)")
        void shouldThrowWhenStayBelongsToDifferentHotel() {
                // Arrange — stayId from another hotel; findByStayIdAndHotelId returns empty (hotel scoping)
                final UUID foreignStayId = UUID.randomUUID();
                final ChargeRequest chargeRequest = new ChargeRequest(
                                ChargeType.FB_ORDER, SIMPLE_DRINK, BigDecimal.valueOf(3), null, null, null);

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
                                        reservationId, guestId, stayId, null, null, List.of(), List.of());
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
                                ChargeType.FB_ORDER, SIMPLE_DRINK, chargeAmount, null, null, null);
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
                                                        c.getVatRate(), c.getNaturaCode(), c.getReferenceId(), null, null, null);
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
                                ChargeType.EXTRA, "Minibar", BigDecimal.TEN, null, null, null);
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
                                                        c.getVatRate(), c.getNaturaCode(), c.getReferenceId(), null, null, null);
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
                                        reservationId, guestId, stayId, null, null, List.of(), List.of());
                });

                // Act
                final InvoiceResponse result = invoiceService.createInvoiceForStay(request);

                // Assert — sequenza incrementata da seqBefore a seqBefore+1
                assertEquals(currentYear + "/000" + (seqBefore + 1), result.invoiceNumber());
                assertEquals(seqBefore + 1, existingSeq.getLastSeq());
        }

        // ---------------------------------------------------------------
        // updateDocumentType (Verticale 4)
        // ---------------------------------------------------------------

        @Test
        @DisplayName("Should switch invoice document type from FATTURA to RICEVUTA")
        void shouldUpdateDocumentTypeToRicevuta() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                invoice.setStatus(InvoiceStatus.ISSUED);
                final InvoiceResponse expected = new InvoiceResponse(invoiceId, hotelId, INV_123, null,
                                BigDecimal.TEN, InvoiceStatus.ISSUED, reservationId, guestId, null,
                                DocumentType.RICEVUTA, null, List.of(), List.of());

                when(invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)).thenReturn(Optional.of(invoice));
                when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);
                when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

                // Act
                final InvoiceResponse result = invoiceService.updateDocumentType(invoiceId, DocumentType.RICEVUTA);

                // Assert
                assertNotNull(result);
                assertEquals(DocumentType.RICEVUTA, result.documentType());
                final ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
                verify(invoiceRepository).save(captor.capture());
                assertEquals(DocumentType.RICEVUTA, captor.getValue().getDocumentType());
        }

        @Test
        @DisplayName("Should throw ConflictException when updating document type on CANCELLED invoice")
        void shouldThrowWhenUpdatingCancelledInvoice() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                invoice.setStatus(InvoiceStatus.CANCELLED);

                when(invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)).thenReturn(Optional.of(invoice));

                // Act & Assert
                assertThrows(InvoiceConflictException.class,
                                () -> invoiceService.updateDocumentType(invoiceId, DocumentType.RICEVUTA));
        }

        @Test
        @DisplayName("Should switch document type on a PAID-but-not-fiscally-exported invoice (pays, then asks for FATTURA)")
        void shouldUpdateDocumentTypeOnPaidInvoiceWhenNotFiscallyLocked() {
                // Arrange — PAID alone must NOT block this: the ordinary real-world sequence is
                // pay in full, then request a proper FatturaPA (RICEVUTA->FATTURA). A blanket
                // CANNOT_UPDATE_PAID_INVOICE guard here previously blocked that legitimate flow
                // (regression found by the 2026-08-24 live QA pass against checkout-live.spec.ts).
                // assertNotFiscallyLocked (invoiceFiscalExportRepository) is the guard that
                // actually matters — see shouldThrowLockedWhenUpdatingDocumentTypeOnExportedInvoice
                // below for the case that must still be blocked.
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                invoice.setStatus(InvoiceStatus.PAID);
                final InvoiceResponse expected = new InvoiceResponse(invoiceId, hotelId, INV_123, null,
                                BigDecimal.TEN, InvoiceStatus.PAID, reservationId, guestId, null,
                                DocumentType.FATTURA, null, List.of(), List.of());

                when(invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)).thenReturn(Optional.of(invoice));
                when(invoiceFiscalExportRepository.existsByInvoiceId(invoiceId)).thenReturn(false);
                when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);
                when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

                // Act
                final InvoiceResponse result = invoiceService.updateDocumentType(invoiceId, DocumentType.FATTURA);

                // Assert
                assertNotNull(result);
                assertEquals(DocumentType.FATTURA, result.documentType());
                final ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
                verify(invoiceRepository).save(captor.capture());
                assertEquals(DocumentType.FATTURA, captor.getValue().getDocumentType());
        }

        @Test
        @DisplayName("Should throw InvoiceConflictException when updating document type on an already-exported invoice")
        void shouldThrowLockedWhenUpdatingDocumentTypeOnExportedInvoice() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                invoice.setStatus(InvoiceStatus.ISSUED);

                when(invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)).thenReturn(Optional.of(invoice));
                when(invoiceFiscalExportRepository.existsByInvoiceId(invoiceId)).thenReturn(true);

                // Act & Assert
                final Exception exception = assertThrows(InvoiceConflictException.class,
                                () -> invoiceService.updateDocumentType(invoiceId, DocumentType.RICEVUTA));
                assertEquals("INVOICE_LOCKED_AFTER_EXPORT", exception.getMessage());
                verify(invoiceRepository, never()).save(any(Invoice.class));
        }

        @Test
        @DisplayName("Should update SDI status to SENT for a FATTURA invoice")
        void shouldUpdateSdiStatusToSent() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                invoice.setStatus(InvoiceStatus.ISSUED);
                invoice.setDocumentType(DocumentType.FATTURA);
                final InvoiceResponse expected = new InvoiceResponse(invoiceId, hotelId, INV_123, null,
                                BigDecimal.TEN, InvoiceStatus.ISSUED, reservationId, guestId, null,
                                DocumentType.FATTURA, SdiStatus.SENT, List.of(), List.of());

                when(invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)).thenReturn(Optional.of(invoice));
                when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);
                when(invoiceMapper.toResponse(invoice)).thenReturn(expected);

                // Act
                final InvoiceResponse result = invoiceService.updateSdiStatus(invoiceId, SdiStatus.SENT);

                // Assert
                assertNotNull(result);
                assertEquals(SdiStatus.SENT, result.sdiStatus());
        }

        @Test
        @DisplayName("Should throw ConflictException when updating SDI status on RICEVUTA invoice")
        void shouldThrowWhenUpdatingSdiStatusOnRicevuta() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                invoice.setStatus(InvoiceStatus.ISSUED);
                invoice.setDocumentType(DocumentType.RICEVUTA);

                when(invoiceRepository.findByIdAndHotelId(invoiceId, hotelId)).thenReturn(Optional.of(invoice));

                // Act & Assert
                assertThrows(InvoiceConflictException.class,
                                () -> invoiceService.updateSdiStatus(invoiceId, SdiStatus.SENT));
        }

        @Test
        @DisplayName("C12: searchInvoices with no query skips guest resolution and passes an empty guestIds list")
        void searchInvoicesWithNoQuerySkipsGuestResolution() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                invoice.setGuestId(guestId);
                final InvoiceResponse mapped = new InvoiceResponse(invoiceId, hotelId, INV_123, null,
                                BigDecimal.TEN, InvoiceStatus.ISSUED, reservationId, guestId, null,
                                null, null, List.of(), List.of());
                final PageRequest pageable = PageRequest.of(PAGE_ZERO, PAGE_SIZE_TWENTY);

                when(invoiceRepository.searchInvoicesByHotelId(eq(hotelId), eq(InvoiceStatus.ISSUED), eq(null), eq(null),
                                eq(null), eq(List.of()), eq(pageable)))
                                .thenReturn(new PageImpl<>(List.of(invoice)));
                when(invoiceMapper.toResponse(invoice)).thenReturn(mapped);
                when(guestClient.getGuestsBatch(List.of(guestId)))
                                .thenReturn(List.of(new GuestResponse(guestId, GUEST_FIRST_NAME_MARIO, "Rossi", "mario@test.com",
                                                null, null, null, null, null, null, null, null, null)));

                // Act
                final Page<InvoiceSearchResultResponse> result = invoiceService.searchInvoices(
                                InvoiceStatus.ISSUED, null, null, null, pageable);

                // Assert
                assertEquals(1, result.getTotalElements());
                assertEquals("Mario Rossi", result.getContent().get(0).guestName());
                verify(guestClient, never()).searchGuests(any(), anyInt());
        }

        @Test
        @DisplayName("C12: searchInvoices with a blank query behaves like no query at all")
        void searchInvoicesWithBlankQueryIsTreatedAsNoQuery() {
                // Arrange
                final PageRequest pageable = PageRequest.of(PAGE_ZERO, PAGE_SIZE_TWENTY);
                when(invoiceRepository.searchInvoicesByHotelId(eq(hotelId), eq(null), eq(null), eq(null),
                                eq(null), eq(List.of()), eq(pageable)))
                                .thenReturn(new PageImpl<>(List.of()));

                // Act
                invoiceService.searchInvoices(null, "   ", null, null, pageable);

                // Assert
                verify(guestClient, never()).searchGuests(any(), anyInt());
        }

        @Test
        @DisplayName("C12: searchInvoices with a query resolves matching guest IDs and passes them through")
        void searchInvoicesWithQueryResolvesGuestIds() {
                // Arrange
                final UUID otherGuestId = UUID.randomUUID();
                final PageRequest pageable = PageRequest.of(PAGE_ZERO, PAGE_SIZE_TWENTY);
                when(guestClient.searchGuests(QUERY_MARIO, GUEST_SEARCH_CAP))
                                .thenReturn(new GuestSearchPageResponse(List.of(
                                                new GuestResponse(otherGuestId, GUEST_FIRST_NAME_MARIO, "Bianchi",
                                                                "mario.b@test.com", null, null, null, null, null,
                                                                null, null, null, null))));
                when(invoiceRepository.searchInvoicesByHotelId(eq(hotelId), eq(null), eq(null), eq(null),
                                eq(QUERY_MARIO), eq(List.of(otherGuestId)), eq(pageable)))
                                .thenReturn(new PageImpl<>(List.of()));

                // Act
                invoiceService.searchInvoices(null, "  " + QUERY_MARIO + "  ", null, null, pageable);

                // Assert: verified via the repository stub matching the resolved guestIds exactly
                verify(invoiceRepository).searchInvoicesByHotelId(eq(hotelId), eq(null), eq(null), eq(null),
                                eq(QUERY_MARIO), eq(List.of(otherGuestId)), eq(pageable));
        }

        @Test
        @DisplayName("C12: searchInvoices converts the date range to an inclusive-day window")
        void searchInvoicesConvertsDateRangeToInclusiveDayWindow() {
                // Arrange
                final LocalDate from = LocalDate.of(SEARCH_YEAR, SEARCH_MONTH, DAY_ONE);
                final LocalDate to = LocalDate.of(SEARCH_YEAR, SEARCH_MONTH, DAY_FIVE);
                final PageRequest pageable = PageRequest.of(PAGE_ZERO, PAGE_SIZE_TWENTY);
                when(invoiceRepository.searchInvoicesByHotelId(eq(hotelId), eq(null),
                                eq(from.atStartOfDay()), eq(to.plusDays(1).atStartOfDay()),
                                eq(null), eq(List.of()), eq(pageable)))
                                .thenReturn(new PageImpl<>(List.of()));

                // Act
                invoiceService.searchInvoices(null, null, from, to, pageable);

                // Assert
                verify(invoiceRepository).searchInvoicesByHotelId(eq(hotelId), eq(null),
                                eq(from.atStartOfDay()), eq(to.plusDays(1).atStartOfDay()),
                                eq(null), eq(List.of()), eq(pageable));
        }

        @Test
        @DisplayName("C12: searchInvoices omits guestName when guest-service resolves nothing (circuit breaker open)")
        void searchInvoicesOmitsGuestNameWhenBatchResolutionFails() {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                invoice.setGuestId(guestId);
                final InvoiceResponse mapped = new InvoiceResponse(invoiceId, hotelId, INV_123, null,
                                BigDecimal.TEN, InvoiceStatus.ISSUED, reservationId, guestId, null,
                                null, null, List.of(), List.of());
                final PageRequest pageable = PageRequest.of(PAGE_ZERO, PAGE_SIZE_TWENTY);

                when(invoiceRepository.searchInvoicesByHotelId(eq(hotelId), eq(null), eq(null), eq(null),
                                eq(null), eq(List.of()), eq(pageable)))
                                .thenReturn(new PageImpl<>(List.of(invoice)));
                when(invoiceMapper.toResponse(invoice)).thenReturn(mapped);
                when(guestClient.getGuestsBatch(List.of(guestId))).thenReturn(List.of());

                // Act
                final Page<InvoiceSearchResultResponse> result =
                                invoiceService.searchInvoices(null, null, null, null, pageable);

                // Assert: fallback fails soft — invoice still returned, just without a guest name
                assertEquals(1, result.getTotalElements());
                assertNull(result.getContent().get(0).guestName());
        }

        // ---------------------------------------------------------------
        // exportInvoicesCsv (Point 3 -- CSV export)
        // ---------------------------------------------------------------

        @Test
        @DisplayName("exportInvoicesCsv writes the header and hotel-scoped, guest-resolved rows")
        void exportInvoicesCsvWritesHeaderAndScopedRows() throws IOException {
                // Arrange
                final UUID invoiceId = UUID.randomUUID();
                final Invoice invoice = new Invoice();
                invoice.setId(invoiceId);
                invoice.setGuestId(guestId);
                invoice.setInvoiceNumber(INV_123);
                invoice.setIssueDate(LocalDateTime.of(SEARCH_YEAR, SEARCH_MONTH, DAY_ONE, 0, 0));
                invoice.setStatus(InvoiceStatus.ISSUED);
                invoice.setDocumentType(DocumentType.FATTURA);
                invoice.setTotalAmount(BigDecimal.TEN);
                final PageRequest pageable = PageRequest.of(
                                PAGE_ZERO, EXPORT_PAGE_SIZE, Sort.by(SORT_FIELD_ISSUE_DATE).descending());

                when(invoiceRepository.searchInvoicesByHotelId(eq(hotelId), eq(InvoiceStatus.ISSUED), eq(null), eq(null),
                                eq(null), eq(List.of()), eq(pageable)))
                                .thenReturn(new PageImpl<>(List.of(invoice)));
                when(guestClient.getGuestsBatch(List.of(guestId)))
                                .thenReturn(List.of(new GuestResponse(guestId, GUEST_FIRST_NAME_MARIO, "Rossi", "mario@test.com",
                                                null, null, null, null, null, null, null, null, null)));

                // Act
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                invoiceService.exportInvoicesCsv(InvoiceStatus.ISSUED, null, null, null, out);

                // Assert
                final String content = out.toString(StandardCharsets.UTF_8);
                assertTrue(content.contains("invoiceNumber;guestName;issueDate;status;documentType;totalAmount"));
                assertTrue(content.contains(INV_123));
                assertTrue(content.contains("Mario Rossi"));
                assertTrue(content.contains(String.valueOf(InvoiceStatus.ISSUED)));
        }

        @Test
        @DisplayName("exportInvoicesCsv skips guest batch resolution when there are no matching invoices")
        void exportInvoicesCsvSkipsGuestBatchResolutionWhenEmpty() throws IOException {
                // Arrange
                final PageRequest pageable = PageRequest.of(
                                PAGE_ZERO, EXPORT_PAGE_SIZE, Sort.by(SORT_FIELD_ISSUE_DATE).descending());
                when(invoiceRepository.searchInvoicesByHotelId(eq(hotelId), eq(null), eq(null), eq(null),
                                eq(null), eq(List.of()), eq(pageable)))
                                .thenReturn(new PageImpl<>(List.of()));

                // Act
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                invoiceService.exportInvoicesCsv(null, null, null, null, out);

                // Assert
                verify(guestClient, never()).getGuestsBatch(any());
                assertTrue(out.toString(StandardCharsets.UTF_8).contains("invoiceNumber;guestName"));
        }

        @Test
        @DisplayName("exportInvoicesCsv resolves matching guest IDs from a free-text query")
        void exportInvoicesCsvResolvesGuestIdsFromQuery() throws IOException {
                // Arrange
                final UUID otherGuestId = UUID.randomUUID();
                final PageRequest pageable = PageRequest.of(
                                PAGE_ZERO, EXPORT_PAGE_SIZE, Sort.by(SORT_FIELD_ISSUE_DATE).descending());
                when(guestClient.searchGuests(QUERY_MARIO, GUEST_SEARCH_CAP))
                                .thenReturn(new GuestSearchPageResponse(List.of(
                                                new GuestResponse(otherGuestId, GUEST_FIRST_NAME_MARIO, "Bianchi",
                                                                "mario.b@test.com", null, null, null, null, null,
                                                                null, null, null, null))));
                when(invoiceRepository.searchInvoicesByHotelId(eq(hotelId), eq(null), eq(null), eq(null),
                                eq(QUERY_MARIO), eq(List.of(otherGuestId)), eq(pageable)))
                                .thenReturn(new PageImpl<>(List.of()));

                // Act
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                invoiceService.exportInvoicesCsv(null, "  " + QUERY_MARIO + "  ", null, null, out);

                // Assert
                verify(invoiceRepository).searchInvoicesByHotelId(eq(hotelId), eq(null), eq(null), eq(null),
                                eq(QUERY_MARIO), eq(List.of(otherGuestId)), eq(pageable));
        }

        // ---------------------------------------------------------------
        // Master folio / group charges (Point 4 -- gestione gruppi)
        // ---------------------------------------------------------------

        @Test
        @DisplayName("createMasterFolioForGroup creates a new MASTER invoice when none exists yet")
        void createMasterFolioForGroupCreatesNewInvoice() {
                final UUID groupId = UUID.randomUUID();
                when(invoiceRepository.findByGroupIdAndHotelIdAndFolioType(groupId, hotelId, FolioType.MASTER))
                                .thenReturn(Optional.empty());
                when(sequenceRepository.findByHotelIdAndYearForUpdate(eq(hotelId), anyInt()))
                                .thenReturn(Optional.empty());
                when(invoiceRepository.save(notNull())).thenAnswer(inv -> inv.getArgument(0));
                when(invoiceMapper.toResponse(any(Invoice.class))).thenReturn(mock(InvoiceResponse.class));

                final InvoiceResponse response =
                                invoiceService.createMasterFolioForGroup(groupId, new MasterFolioRequest(guestId));

                assertNotNull(response);
                final ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
                verify(invoiceRepository).save(captor.capture());
                assertEquals(FolioType.MASTER, captor.getValue().getFolioType());
                assertEquals(groupId, captor.getValue().getGroupId());
                assertEquals(guestId, captor.getValue().getGuestId());
                assertEquals(InvoiceStatus.ISSUED, captor.getValue().getStatus());
                assertEquals(BigDecimal.ZERO, captor.getValue().getTotalAmount());
        }

        @Test
        @DisplayName("createMasterFolioForGroup returns the existing ISSUED master folio instead of duplicating it")
        void createMasterFolioForGroupReturnsExistingOpenFolio() {
                final UUID groupId = UUID.randomUUID();
                final Invoice existing = Invoice.builder()
                                .id(UUID.randomUUID())
                                .groupId(groupId)
                                .folioType(FolioType.MASTER)
                                .status(InvoiceStatus.ISSUED)
                                .guestId(guestId)
                                .totalAmount(BigDecimal.ZERO)
                                .invoiceNumber(INV_123)
                                .build();
                when(invoiceRepository.findByGroupIdAndHotelIdAndFolioType(groupId, hotelId, FolioType.MASTER))
                                .thenReturn(Optional.of(existing));
                when(invoiceMapper.toResponse(existing)).thenReturn(mock(InvoiceResponse.class));

                invoiceService.createMasterFolioForGroup(groupId, new MasterFolioRequest(guestId));

                verify(invoiceRepository, never()).save(any(Invoice.class));
        }

        @Test
        @DisplayName("addChargeToGroupFolio adds a charge and increments the master folio total")
        void addChargeToGroupFolioAddsChargeAndUpdatesTotal() {
                final UUID groupId = UUID.randomUUID();
                final UUID stayId = UUID.randomUUID();
                final Invoice masterFolio = Invoice.builder()
                                .id(UUID.randomUUID())
                                .groupId(groupId)
                                .folioType(FolioType.MASTER)
                                .status(InvoiceStatus.ISSUED)
                                .totalAmount(BigDecimal.ZERO)
                                .invoiceNumber(INV_123)
                                .build();
                when(invoiceRepository.findByGroupIdAndHotelIdAndFolioType(groupId, hotelId, FolioType.MASTER))
                                .thenReturn(Optional.of(masterFolio));
                when(invoiceChargeRepository.save(notNull())).thenAnswer(inv -> inv.getArgument(0));
                when(invoiceChargeMapper.toResponse(any(InvoiceCharge.class))).thenReturn(mock(ChargeResponse.class));

                final GroupChargeRequest request = new GroupChargeRequest(
                                ChargeType.ROOM_NIGHT, "Room 101 - 2 night(s)", new BigDecimal("200.00"),
                                new BigDecimal("100.00"), 2, stayId);

                invoiceService.addChargeToGroupFolio(groupId, request);

                assertEquals(new BigDecimal("200.00"), masterFolio.getTotalAmount());
                final ArgumentCaptor<InvoiceCharge> captor = ArgumentCaptor.forClass(InvoiceCharge.class);
                verify(invoiceChargeRepository).save(captor.capture());
                assertEquals(stayId, captor.getValue().getRoutedFromStayId());
        }

        @Test
        @DisplayName("addChargeToGroupFolio throws NotFoundException when the group has no master folio")
        void addChargeToGroupFolioThrowsWhenNoMasterFolioExists() {
                final UUID groupId = UUID.randomUUID();
                when(invoiceRepository.findByGroupIdAndHotelIdAndFolioType(groupId, hotelId, FolioType.MASTER))
                                .thenReturn(Optional.empty());

                final GroupChargeRequest request = new GroupChargeRequest(
                                ChargeType.ROOM_NIGHT, "Room 101", BigDecimal.TEN, null, null, UUID.randomUUID());

                assertThrows(NotFoundException.class,
                                () -> invoiceService.addChargeToGroupFolio(groupId, request));
        }

        @Test
        @DisplayName("addChargeToGroupFolio rejects a charge when the master folio is no longer ISSUED")
        void addChargeToGroupFolioThrowsWhenMasterFolioNotOpen() {
                final UUID groupId = UUID.randomUUID();
                final Invoice paidMasterFolio = Invoice.builder()
                                .id(UUID.randomUUID())
                                .groupId(groupId)
                                .folioType(FolioType.MASTER)
                                .status(InvoiceStatus.PAID)
                                .totalAmount(BigDecimal.TEN)
                                .invoiceNumber(INV_123)
                                .build();
                when(invoiceRepository.findByGroupIdAndHotelIdAndFolioType(groupId, hotelId, FolioType.MASTER))
                                .thenReturn(Optional.of(paidMasterFolio));

                final GroupChargeRequest request = new GroupChargeRequest(
                                ChargeType.ROOM_NIGHT, "Room 101", BigDecimal.TEN, null, null, UUID.randomUUID());

                assertThrows(InvoiceConflictException.class,
                                () -> invoiceService.addChargeToGroupFolio(groupId, request));
        }
}
