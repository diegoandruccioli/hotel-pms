package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.HotelSettingsClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.HotelSettingsResponse;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.InvoiceFiscalExport;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.SdiStatus;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.exception.BillingValidationException;
import com.hotelpms.billing.exception.InvoiceConflictException;
import com.hotelpms.billing.repository.InvoiceFiscalExportRepository;
import com.hotelpms.billing.service.FatturaPaXsdValidator;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.VatBreakdownCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.hotelpms.billing.domain.PaymentMethod;
import com.hotelpms.billing.dto.PaymentResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class FatturaPAServiceImplTest {

    private static final UUID INVOICE_ID = Objects.requireNonNull(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UUID HOTEL_ID = Objects.requireNonNull(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final UUID GUEST_ID = Objects.requireNonNull(UUID.fromString("00000000-0000-0000-0000-000000000003"));
    private static final UUID RES_ID = Objects.requireNonNull(UUID.fromString("00000000-0000-0000-0000-000000000004"));
    private static final int ISSUE_YEAR = 2026;
    private static final int ISSUE_DAY = 15;
    private static final String HOTEL_NAME = "Hotel Test";
    private static final String GUEST_FIRST_NAME = "Mario";
    private static final String GUEST_LAST_NAME = "Rossi";
    private static final String GUEST_EMAIL = "mario@rossi.it";
    private static final String GUEST_ADDRESS = "Via Milano 5";
    private static final String INVOICE_NUMBER_1 = "2026/0001";
    private static final String INVOICE_NUMBER_2 = "2026/0002";
    private static final String INDEX_CSV = "index.csv";

    @Mock
    private InvoiceService invoiceService;
    @Mock
    private HotelSettingsClient hotelSettingsClient;
    @Mock
    private GuestClient guestClient;
    @Spy
    private final VatBreakdownCalculator vatBreakdownCalculator = new VatBreakdownCalculator();
    // Mockito default answer for a List-returning method is an empty list, i.e. "no
    // validation errors" — matches what every test here expects except the dedicated
    // schema-validation test below, which stubs a non-empty result explicitly.
    @Mock
    private FatturaPaXsdValidator xsdValidator;

    @Mock
    private InvoiceFiscalExportRepository invoiceFiscalExportRepository;

    @InjectMocks
    private FatturaPAServiceImpl service;

    private HotelSettingsResponse hotel;
    private GuestResponse guest;

    @BeforeEach
    void setUp() {
        hotel = new HotelSettingsResponse(HOTEL_ID, HOTEL_NAME, "Via Roma 1", "12345678901", "TSTDNL80A01H501W", null,
                "00100", "Roma", "RM");
        guest = new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME, GUEST_EMAIL,
                "RSSMRA80A01H501T", null, null, "ABC1234", null,
                GUEST_ADDRESS, "20100", "Milano", "MI");
    }

    @Test
    void shouldGenerateValidXmlForFattura() {
        final InvoiceResponse invoice = fattura(InvoiceStatus.ISSUED, DocumentType.FATTURA);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);
        when(hotelSettingsClient.getSettings()).thenReturn(hotel);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);

        final byte[] xml = service.generateXml(INVOICE_ID);

        assertThat(xml).isNotNull().isNotEmpty();
        final String xmlStr = new String(xml, StandardCharsets.UTF_8);
        assertThat(xmlStr).contains("FatturaElettronica");
        assertThat(xmlStr).contains("FPR12");
        assertThat(xmlStr).contains("TD01");
        assertThat(xmlStr).contains("EUR");
        assertThat(xmlStr).contains(HOTEL_NAME);
        assertThat(xmlStr).contains("ABC1234");
        // invoice total must appear in the fallback line (no charges → use totalAmount)
        assertThat(xmlStr).contains("100.00"); // imponibile of 110.00 at 10% VAT
        // no payments → default MP05 with the full invoice amount
        assertThat(xmlStr).contains("<ModalitaPagamento>MP05</ModalitaPagamento>");
        assertThat(xmlStr).contains("<ImportoPagamento>110.00</ImportoPagamento>");

        final org.mockito.ArgumentCaptor<InvoiceFiscalExport> captor =
                org.mockito.ArgumentCaptor.forClass(InvoiceFiscalExport.class);
        verify(invoiceFiscalExportRepository).save(captor.capture());
        final InvoiceFiscalExport export = captor.getValue();
        assertThat(export.getInvoiceId()).isEqualTo(INVOICE_ID);
        assertThat(export.getHotelId()).isEqualTo(HOTEL_ID);
        assertThat(export.getXmlPayload()).isEqualTo(xml);
        assertThat(export.getPayloadSha256()).hasSize(64);
        assertThat(export.getExportedAt()).isNotNull();
    }

    @Test
    void shouldThrowWhenGeneratedXmlFailsSchemaValidation() {
        final InvoiceResponse invoice = fattura(InvoiceStatus.ISSUED, DocumentType.FATTURA);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);
        when(hotelSettingsClient.getSettings()).thenReturn(hotel);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);
        when(xsdValidator.validate(any()))
                .thenReturn(List.of("line 12: unexpected element 'Foo'"));

        assertThatThrownBy(() -> service.generateXml(INVOICE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FATTURAPA_XML_SCHEMA_INVALID");
        verify(invoiceFiscalExportRepository, never()).save(any());
    }

    @Test
    void shouldUseGuestSdiCodeInCodiceDestinatario() {
        final InvoiceResponse invoice = fattura(InvoiceStatus.ISSUED, DocumentType.FATTURA);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);
        when(hotelSettingsClient.getSettings()).thenReturn(hotel);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);

        final String xmlStr = new String(service.generateXml(INVOICE_ID), StandardCharsets.UTF_8);

        assertThat(xmlStr).contains("<CodiceDestinatario>ABC1234</CodiceDestinatario>");
    }

    @Test
    void shouldFallbackToDefaultDestinatarioWhenNoSdiCode() {
        final GuestResponse noSdi = new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME,
                GUEST_EMAIL, null, null, null, null, null,
                GUEST_ADDRESS, "20100", "Milano", "MI");
        final InvoiceResponse invoice = fattura(InvoiceStatus.ISSUED, DocumentType.FATTURA);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);
        when(hotelSettingsClient.getSettings()).thenReturn(hotel);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(noSdi);

        final String xmlStr = new String(service.generateXml(INVOICE_ID), StandardCharsets.UTF_8);

        assertThat(xmlStr).contains("<CodiceDestinatario>0000000</CodiceDestinatario>");
    }

    @Test
    void shouldRejectExportWhenHotelStructuredAddressIsIncomplete() {
        final HotelSettingsResponse incompleteHotel = new HotelSettingsResponse(
                HOTEL_ID, HOTEL_NAME, "Via Roma 1", "12345678901", "TSTDNL80A01H501W", null,
                null, null, null);
        final InvoiceResponse invoice = fattura(InvoiceStatus.ISSUED, DocumentType.FATTURA);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);
        when(hotelSettingsClient.getSettings()).thenReturn(incompleteHotel);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);

        assertThatThrownBy(() -> service.generateXml(INVOICE_ID))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("HOTEL_STRUCTURED_ADDRESS_INCOMPLETE");
    }

    @Test
    void shouldRejectExportWhenGuestStructuredAddressIsIncomplete() {
        final GuestResponse incompleteGuest = new GuestResponse(GUEST_ID, GUEST_FIRST_NAME, GUEST_LAST_NAME,
                GUEST_EMAIL, null, null, null, null, null,
                GUEST_ADDRESS, null, null, null);
        final InvoiceResponse invoice = fattura(InvoiceStatus.ISSUED, DocumentType.FATTURA);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);
        when(hotelSettingsClient.getSettings()).thenReturn(hotel);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(incompleteGuest);

        assertThatThrownBy(() -> service.generateXml(INVOICE_ID))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("GUEST_STRUCTURED_ADDRESS_INCOMPLETE");
    }

    @Test
    void shouldThrowWhenDocumentTypeIsRicevuta() {
        final InvoiceResponse invoice = fattura(InvoiceStatus.ISSUED, DocumentType.RICEVUTA);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        assertThatThrownBy(() -> service.generateXml(INVOICE_ID))
                .isInstanceOf(InvoiceConflictException.class);
    }

    @Test
    void shouldThrowWhenInvoiceIsCancelled() {
        final InvoiceResponse invoice = fattura(InvoiceStatus.CANCELLED, DocumentType.FATTURA);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        assertThatThrownBy(() -> service.generateXml(INVOICE_ID))
                .isInstanceOf(InvoiceConflictException.class);
    }

    static Stream<Arguments> paymentMethodCodiceProvider() {
        return Stream.of(
                Arguments.of(PaymentMethod.CASH, "MP01"),
                Arguments.of(PaymentMethod.CREDIT_CARD, "MP08"),
                Arguments.of(PaymentMethod.DEBIT_CARD, "MP08"),
                Arguments.of(PaymentMethod.BANK_TRANSFER, "MP05"),
                Arguments.of(PaymentMethod.CHECK, "MP02")
        );
    }

    @ParameterizedTest
    @MethodSource("paymentMethodCodiceProvider")
    void shouldMapPaymentMethodToCorrectMPCode(final PaymentMethod method, final String expectedCode) {
        final BigDecimal amnt = new BigDecimal("100.00");
        final PaymentResponse payment = new PaymentResponse(
                UUID.randomUUID(), LocalDateTime.of(ISSUE_YEAR, 1, ISSUE_DAY, 10, 0),
                amnt, method, null, INVOICE_ID);
        final InvoiceResponse invoice = new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, INVOICE_NUMBER_1,
                LocalDateTime.of(ISSUE_YEAR, 1, ISSUE_DAY, 10, 0),
                amnt, InvoiceStatus.ISSUED,
                RES_ID, GUEST_ID, null,
                DocumentType.FATTURA, SdiStatus.NOT_SENT, List.of(payment), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);
        when(hotelSettingsClient.getSettings()).thenReturn(hotel);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);

        final String xmlStr = new String(service.generateXml(INVOICE_ID), StandardCharsets.UTF_8);

        assertThat(xmlStr).contains("<ModalitaPagamento>" + expectedCode + "</ModalitaPagamento>");
    }

    @Test
    void shouldBuildZipWithOneXmlPerEligibleInvoicePlusIndex() throws java.io.IOException {
        final UUID otherInvoiceId = UUID.randomUUID();
        final InvoiceResponse first = fattura(InvoiceStatus.ISSUED, DocumentType.FATTURA);
        final InvoiceResponse second = new InvoiceResponse(
                otherInvoiceId, HOTEL_ID, INVOICE_NUMBER_2,
                LocalDateTime.of(ISSUE_YEAR, 1, ISSUE_DAY, 11, 0),
                new BigDecimal("55.00"), InvoiceStatus.ISSUED,
                RES_ID, GUEST_ID, null,
                DocumentType.FATTURA, SdiStatus.NOT_SENT, List.of(), List.of());
        final LocalDate from = LocalDate.of(ISSUE_YEAR, 1, 1);
        final LocalDate to = LocalDate.of(ISSUE_YEAR, 1, 31);

        when(invoiceService.getInvoicesInPeriod(from, to)).thenReturn(List.of(first, second));
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(first);
        when(invoiceService.getInvoice(otherInvoiceId)).thenReturn(second);
        when(hotelSettingsClient.getSettings()).thenReturn(hotel);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);

        final byte[] zip = service.generateBatchZip(from, to);
        final ZipContents contents = readZip(zip);

        assertThat(contents.entryNames).containsExactlyInAnyOrder("2026-0001.xml", "2026-0002.xml", INDEX_CSV);
        assertThat(contents.index).contains(INVOICE_NUMBER_1).contains(INVOICE_NUMBER_2);
        verify(invoiceFiscalExportRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void shouldSkipInvoiceWithIncompleteGuestAddressAndStillExportTheRest() throws java.io.IOException {
        final UUID brokenInvoiceId = UUID.randomUUID();
        final UUID brokenGuestId = UUID.randomUUID();
        final InvoiceResponse broken = new InvoiceResponse(
                brokenInvoiceId, HOTEL_ID, INVOICE_NUMBER_2,
                LocalDateTime.of(ISSUE_YEAR, 1, ISSUE_DAY, 11, 0),
                new BigDecimal("55.00"), InvoiceStatus.ISSUED,
                RES_ID, brokenGuestId, null,
                DocumentType.FATTURA, SdiStatus.NOT_SENT, List.of(), List.of());
        final GuestResponse incompleteGuest = new GuestResponse(brokenGuestId, GUEST_FIRST_NAME, GUEST_LAST_NAME,
                GUEST_EMAIL, null, null, null, null, null,
                GUEST_ADDRESS, null, null, null);
        final InvoiceResponse ok = fattura(InvoiceStatus.ISSUED, DocumentType.FATTURA);
        final LocalDate from = LocalDate.of(ISSUE_YEAR, 1, 1);
        final LocalDate to = LocalDate.of(ISSUE_YEAR, 1, 31);

        when(invoiceService.getInvoicesInPeriod(from, to)).thenReturn(List.of(broken, ok));
        when(invoiceService.getInvoice(brokenInvoiceId)).thenReturn(broken);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(ok);
        when(hotelSettingsClient.getSettings()).thenReturn(hotel);
        when(guestClient.getGuestById(brokenGuestId)).thenReturn(incompleteGuest);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);

        final byte[] zip = service.generateBatchZip(from, to);
        final ZipContents contents = readZip(zip);

        assertThat(contents.entryNames).containsExactlyInAnyOrder("2026-0001.xml", INDEX_CSV);
        assertThat(contents.index).contains("ERROR: GUEST_STRUCTURED_ADDRESS_INCOMPLETE");
        verify(invoiceFiscalExportRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void shouldExcludeCancelledAndRicevutaInvoicesFromBatchExport() throws java.io.IOException {
        final InvoiceResponse cancelled = fattura(InvoiceStatus.CANCELLED, DocumentType.FATTURA);
        final InvoiceResponse ricevuta = fattura(InvoiceStatus.ISSUED, DocumentType.RICEVUTA);
        final LocalDate from = LocalDate.of(ISSUE_YEAR, 1, 1);
        final LocalDate to = LocalDate.of(ISSUE_YEAR, 1, 31);

        when(invoiceService.getInvoicesInPeriod(from, to)).thenReturn(List.of(cancelled, ricevuta));

        final byte[] zip = service.generateBatchZip(from, to);
        final ZipContents contents = readZip(zip);

        assertThat(contents.entryNames).containsExactly(INDEX_CSV);
        verify(invoiceFiscalExportRepository, never()).save(any());
    }

    @Test
    void shouldRejectBatchExportWhenFromIsAfterTo() {
        final LocalDate from = LocalDate.of(ISSUE_YEAR, 2, 1);
        final LocalDate to = LocalDate.of(ISSUE_YEAR, 1, 1);

        assertThatThrownBy(() -> service.generateBatchZip(from, to))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("EXPORT_PERIOD_INVALID");
    }

    private InvoiceResponse fattura(final InvoiceStatus status, final DocumentType docType) {
        return new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, INVOICE_NUMBER_1,
                LocalDateTime.of(ISSUE_YEAR, 1, ISSUE_DAY, 10, 0),
                new BigDecimal("110.00"), status,
                RES_ID, GUEST_ID, null,
                docType, SdiStatus.NOT_SENT, List.of(), List.of());
    }

    private static ZipContents readZip(final byte[] zip) throws java.io.IOException {
        final List<String> entryNames = new ArrayList<>();
        String index = "";
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                entryNames.add(entry.getName());
                if (INDEX_CSV.equals(entry.getName())) {
                    index = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
                entry = zis.getNextEntry();
            }
        }
        return new ZipContents(entryNames, index);
    }

    private record ZipContents(List<String> entryNames, String index) {
    }
}
