package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.HotelSettingsClient;
import com.hotelpms.billing.client.dto.GuestResponse;
import com.hotelpms.billing.client.dto.HotelSettingsResponse;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.SdiStatus;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.exception.InvoiceConflictException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FatturaPAServiceImplTest {

    private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID RES_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final int ISSUE_YEAR = 2026;
    private static final int ISSUE_DAY = 15;

    @Mock
    private InvoiceService invoiceService;
    @Mock
    private HotelSettingsClient hotelSettingsClient;
    @Mock
    private GuestClient guestClient;

    @InjectMocks
    private FatturaPAServiceImpl service;

    private HotelSettingsResponse hotel;
    private GuestResponse guest;

    @BeforeEach
    void setUp() {
        hotel = new HotelSettingsResponse(HOTEL_ID, "Hotel Test", "Via Roma 1", "12345678901", "TSTDNL80A01H501W", null);
        guest = new GuestResponse(GUEST_ID, "Mario", "Rossi", "mario@rossi.it",
                "RSSMRA80A01H501T", null, null, "ABC1234", null);
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
        assertThat(xmlStr).contains("Hotel Test");
        assertThat(xmlStr).contains("ABC1234");
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
        final GuestResponse noSdi = new GuestResponse(GUEST_ID, "Mario", "Rossi",
                "mario@rossi.it", null, null, null, null, null);
        final InvoiceResponse invoice = fattura(InvoiceStatus.ISSUED, DocumentType.FATTURA);
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoice);
        when(hotelSettingsClient.getSettings()).thenReturn(hotel);
        when(guestClient.getGuestById(GUEST_ID)).thenReturn(noSdi);

        final String xmlStr = new String(service.generateXml(INVOICE_ID), StandardCharsets.UTF_8);

        assertThat(xmlStr).contains("<CodiceDestinatario>0000000</CodiceDestinatario>");
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

    private InvoiceResponse fattura(final InvoiceStatus status, final DocumentType docType) {
        return new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, "2026/0001",
                LocalDateTime.of(ISSUE_YEAR, 1, ISSUE_DAY, 10, 0),
                new BigDecimal("110.00"), status,
                RES_ID, GUEST_ID, null,
                docType, SdiStatus.NOT_SENT, List.of(), List.of());
    }
}
