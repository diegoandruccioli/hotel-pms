package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.HotelSettingsClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.HotelSettingsResponse;
import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.PaymentMethod;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
    private static final int PDF_MAGIC_BYTES_LEN = 5;
    private static final BigDecimal AMOUNT_50 = BigDecimal.valueOf(50);
    private static final BigDecimal AMOUNT_100 = BigDecimal.valueOf(100);

    @Mock
    private InvoiceService invoiceService;
    @Mock
    private HotelSettingsClient hotelSettingsClient;
    @Mock
    private GuestClient guestClient;

    @InjectMocks
    private PdfInvoiceServiceImpl pdfInvoiceService;

    @BeforeEach
    void setUp() {
        final HotelSettingsResponse hotelSettings = new HotelSettingsResponse(
                HOTEL_ID, "Hotel Bella Vista", "Via Roma 1, Milano",
                "01234567890", "BLLVST80A01F205X", null);
        final GuestResponse guest = new GuestResponse(GUEST_ID, "Mario", "Rossi", "mario@example.com");
        when(hotelSettingsClient.getSettings()).thenReturn(hotelSettings);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(guest);
    }

    @Test
    void shouldReturnNonEmptyPdfForBasicInvoice() {
        final InvoiceResponse invoice = buildInvoice(List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        final byte[] pdf = pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        assertThat(pdf).isNotNull().isNotEmpty();
        // PDF magic number: %PDF-
        assertThat(new String(pdf, 0, PDF_MAGIC_BYTES_LEN, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void shouldReturnPdfWithChargesAndPayments() {
        final ChargeResponse charge = new ChargeResponse(
                UUID.randomUUID(), INVOICE_ID, ChargeType.FB_ORDER,
                "Cena ristorante", AMOUNT_50, null, LocalDateTime.now());
        final PaymentResponse payment = new PaymentResponse(
                UUID.randomUUID(), LocalDateTime.now(), AMOUNT_150,
                PaymentMethod.CASH, "TXN-001", INVOICE_ID);
        final InvoiceResponse invoice = buildInvoice(List.of(charge), List.of(payment));
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        final byte[] pdf = pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    void shouldReturnPdfWhenHotelSettingsFallback() {
        final HotelSettingsResponse fallback = new HotelSettingsResponse(
                null, "Hotel", "", "", "", null);
        when(hotelSettingsClient.getSettings()).thenReturn(fallback);
        final InvoiceResponse invoice = buildInvoice(List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        final byte[] pdf = pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    void shouldReturnPdfWithNullIssueDateGracefully() {
        final InvoiceResponse invoice = new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, "INV-001", null,
                AMOUNT_150, InvoiceStatus.ISSUED,
                RESERVATION_ID, GUEST_ID, null,
                List.of(), List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        final byte[] pdf = pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    void shouldReturnPdfWithMultipleCharges() {
        final List<ChargeResponse> charges = List.of(
                new ChargeResponse(UUID.randomUUID(), INVOICE_ID, ChargeType.ROOM_NIGHT,
                        "Camera doppia — 3 notti", AMOUNT_100, null, LocalDateTime.now()),
                new ChargeResponse(UUID.randomUUID(), INVOICE_ID, ChargeType.FB_ORDER,
                        "Colazione", AMOUNT_50, null, LocalDateTime.now()),
                new ChargeResponse(UUID.randomUUID(), INVOICE_ID, ChargeType.EXTRA,
                        "Parcheggio", BigDecimal.TEN, null, LocalDateTime.now()));
        final InvoiceResponse invoice = buildInvoice(charges, List.of());
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);

        final byte[] pdf = pdfInvoiceService.generateInvoicePdf(INVOICE_ID);

        assertThat(pdf).isNotNull().isNotEmpty();
    }

    private InvoiceResponse buildInvoice(final List<ChargeResponse> charges,
                                          final List<PaymentResponse> payments) {
        return new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, "INV-TEST-001",
                LocalDateTime.of(ISSUE_YEAR, ISSUE_MONTH, ISSUE_DAY, ISSUE_HOUR, 0),
                AMOUNT_150, InvoiceStatus.PAID,
                RESERVATION_ID, GUEST_ID, null,
                payments, charges);
    }
}
