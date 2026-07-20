package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.HotelSettingsClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.HotelSettingsResponse;
import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.PaymentMethod;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.VatBreakdownCalculator;
import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"null", "unchecked"})
@ExtendWith(MockitoExtension.class)
class PdfInvoiceServiceImplTest {

    private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final BigDecimal AMOUNT_150 = BigDecimal.valueOf(150);
    private static final int ISSUE_YEAR = 2026;
    private static final int ISSUE_MONTH = 5;
    private static final int ISSUE_DAY = 14;
    private static final int ISSUE_HOUR = 10;
    private static final byte[] FAKE_PDF_BYTES = "%PDF-fake".getBytes(StandardCharsets.US_ASCII);
    private static final BigDecimal AMOUNT_50 = BigDecimal.valueOf(50);
    private static final BigDecimal AMOUNT_100 = BigDecimal.valueOf(100);
    private static final BigDecimal VAT_RATE_10 = new BigDecimal("0.10");
    private static final BigDecimal VAT_RATE_22 = new BigDecimal("0.22");
    private static final String TEMPLATE_FATTURA = "invoice-fattura";
    private static final String TEMPLATE_RICEVUTA = "invoice-ricevuta";
    private static final String VAT_BREAKDOWN_KEY = "vatBreakdown";

    @Mock
    private InvoiceService invoiceService;
    @Mock
    private HotelSettingsClient hotelSettingsClient;
    @Mock
    private GuestClient guestClient;
    @Mock
    private PdfTemplateRenderer pdfTemplateRenderer;
    @Spy
    private final VatBreakdownCalculator vatBreakdownCalculator = new VatBreakdownCalculator();

    @InjectMocks
    private PdfInvoiceServiceImpl pdfInvoiceService;

    @BeforeEach
    void setUp() {
        final HotelSettingsResponse hotelSettings = new HotelSettingsResponse(
                HOTEL_ID, "Hotel Bella Vista", "Via Roma 1, Milano",
                "01234567890", "BLLVST80A01F205X", null, null, null, null);
        final GuestResponse guest = new GuestResponse(GUEST_ID, "Mario", "Rossi", "mario@example.com",
                null, null, null, null, null, null, null, null, null);
        when(hotelSettingsClient.getSettings()).thenReturn(hotelSettings);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);
        when(pdfTemplateRenderer.render(anyString(), anyMap())).thenReturn(FAKE_PDF_BYTES);
    }

    private Map<String, Object> captureRenderContext(final String expectedTemplate) {
        final ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pdfTemplateRenderer).render(eq(expectedTemplate), captor.capture());
        return captor.getValue();
    }

    @Test
    void returnsTheBytesProducedByTheRenderer() {
        final InvoiceResponse invoice = buildInvoice(List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        final byte[] pdf = pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        assertThat(pdf).isEqualTo(FAKE_PDF_BYTES);
    }

    @Test
    void selectsTheFatturaTemplateAndIncludesVatBreakdownForFattura() {
        final List<ChargeResponse> charges = List.of(
                new ChargeResponse(UUID.randomUUID(), INVOICE_ID, ChargeType.ROOM_NIGHT,
                        "Camera doppia", AMOUNT_100, VAT_RATE_10, null, LocalDateTime.now()));
        final InvoiceResponse invoice = buildInvoice(charges, List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        final Map<String, Object> context = captureRenderContext(TEMPLATE_FATTURA);
        assertThat(context).containsEntry("docTitle", "FATTURA");
        assertThat(context).containsKey(VAT_BREAKDOWN_KEY);
        assertThat((List<?>) context.get(VAT_BREAKDOWN_KEY)).isNotEmpty();
    }

    @Test
    void selectsTheRicevutaTemplateAndOmitsVatBreakdownForRicevuta() {
        final InvoiceResponse ricevuta = new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, "RIC-TEST-001",
                LocalDateTime.of(ISSUE_YEAR, ISSUE_MONTH, ISSUE_DAY, ISSUE_HOUR, 0),
                AMOUNT_150, InvoiceStatus.PAID,
                RESERVATION_ID, GUEST_ID, null,
                DocumentType.RICEVUTA, null, List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(ricevuta);

        pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        final Map<String, Object> context = captureRenderContext(TEMPLATE_RICEVUTA);
        assertThat(context).containsEntry("docTitle", "RICEVUTA");
        assertThat(context).doesNotContainKey(VAT_BREAKDOWN_KEY);
    }

    @Test
    void defaultsToFatturaTemplateWhenDocumentTypeIsNull() {
        final InvoiceResponse invoice = new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, "INV-001", null,
                AMOUNT_150, InvoiceStatus.ISSUED,
                RESERVATION_ID, GUEST_ID, null,
                null, null, List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        captureRenderContext(TEMPLATE_FATTURA);
    }

    @Test
    void includesChargesAndPaymentsInTheContext() {
        final ChargeResponse charge = new ChargeResponse(
                UUID.randomUUID(), INVOICE_ID, ChargeType.FB_ORDER,
                "Cena ristorante", AMOUNT_50, VAT_RATE_10, null, LocalDateTime.now());
        final PaymentResponse payment = new PaymentResponse(
                UUID.randomUUID(), LocalDateTime.now(), AMOUNT_150,
                PaymentMethod.CASH, "TXN-001", INVOICE_ID);
        final InvoiceResponse invoice = buildInvoice(List.of(charge), List.of(payment));
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        final Map<String, Object> context = captureRenderContext(TEMPLATE_FATTURA);
        assertThat((List<?>) context.get("charges")).hasSize(1);
        assertThat((List<?>) context.get("payments")).hasSize(1);
    }

    @Test
    void showsCompanyNameAsDisplayNameWithPersonalNameAsSecondaryLine() {
        final GuestResponse fiscalGuest = new GuestResponse(GUEST_ID, "Mario", "Rossi", "mario@example.com",
                "RSSMRA74D22A001Q", "01234567890", "Hotel Srl", "ABCDE12", "mario@pec.it", null, null, null, null);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(fiscalGuest);
        final InvoiceResponse invoice = buildInvoice(List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        final Map<String, Object> context = captureRenderContext(TEMPLATE_FATTURA);
        assertThat(context).containsEntry("guestDisplayName", "Hotel Srl");
        assertThat(context).containsEntry("guestPersonalName", "Mario Rossi");
        assertThat(context).containsEntry("guestFiscalCode", "RSSMRA74D22A001Q");
        assertThat(context).containsEntry("guestPec", "mario@pec.it");
    }

    @Test
    void showsPersonalNameAsDisplayNameWhenNoCompany() {
        final InvoiceResponse invoice = buildInvoice(List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        final Map<String, Object> context = captureRenderContext(TEMPLATE_FATTURA);
        assertThat(context).containsEntry("guestDisplayName", "Mario Rossi");
        assertThat(context).containsEntry("guestPersonalName", null);
    }

    @Test
    void blankHotelSettingsFieldsAreOmittedFromTheContext() {
        final HotelSettingsResponse fallback = new HotelSettingsResponse(
                null, "Hotel", "", "", "", null, null, null, null);
        when(hotelSettingsClient.getSettings()).thenReturn(fallback);
        final InvoiceResponse invoice = buildInvoice(List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        final Map<String, Object> context = captureRenderContext(TEMPLATE_FATTURA);
        assertThat(context).containsEntry("hotelName", "Hotel");
        assertThat(context).containsEntry("hotelAddress", null);
        assertThat(context).containsEntry("hotelVat", null);
        assertThat(context).containsEntry("hotelFiscalCode", null);
    }

    @Test
    void fallsBackToPlaceholderWhenIssueDateIsNull() {
        final InvoiceResponse invoice = new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, "INV-001", null,
                AMOUNT_150, InvoiceStatus.ISSUED,
                RESERVATION_ID, GUEST_ID, null,
                DocumentType.FATTURA, null, List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        final Map<String, Object> context = captureRenderContext(TEMPLATE_FATTURA);
        assertThat(context).containsEntry("issueDate", "---");
    }

    @Test
    void groupsVatBreakdownByRateForMixedCharges() {
        final List<ChargeResponse> charges = List.of(
                new ChargeResponse(UUID.randomUUID(), INVOICE_ID, ChargeType.ROOM_NIGHT,
                        "Camera doppia", AMOUNT_100, VAT_RATE_10, null, LocalDateTime.now()),
                new ChargeResponse(UUID.randomUUID(), INVOICE_ID, ChargeType.EXTRA,
                        "Minibar", BigDecimal.TEN, VAT_RATE_22, null, LocalDateTime.now()));
        final InvoiceResponse invoice = buildInvoice(charges, List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        final Map<String, Object> context = captureRenderContext(TEMPLATE_FATTURA);
        assertThat((List<?>) context.get(VAT_BREAKDOWN_KEY)).hasSize(2);
    }

    private InvoiceResponse buildInvoice(final List<ChargeResponse> charges,
                                          final List<PaymentResponse> payments) {
        final BigDecimal totalAmount = charges.isEmpty() ? AMOUNT_150
                : charges.stream().map(ChargeResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, "INV-TEST-001",
                LocalDateTime.of(ISSUE_YEAR, ISSUE_MONTH, ISSUE_DAY, ISSUE_HOUR, 0),
                totalAmount, InvoiceStatus.PAID,
                RESERVATION_ID, GUEST_ID, null,
                DocumentType.FATTURA, null, payments, charges);
    }
}
