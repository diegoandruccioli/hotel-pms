package com.hotelpms.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.ChargeRequest;
import com.hotelpms.billing.dto.ChargeResponse;
import com.hotelpms.billing.dto.DocumentTypeRequest;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.exception.GlobalExceptionHandler;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.PdfInvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    private static final String BASE_URL = "/api/v1/invoices";
    private static final String PATH_BY_ID = "/{id}";
    private static final String PATH_STAY = "/stay";
    private static final String PATH_STAY_CHARGES = "/stay/{stayId}/charges";
    private static final String PATH_RESERVATION_LATEST = "/reservation/{reservationId}/latest";
    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID STAY_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final BigDecimal AMOUNT_100 = BigDecimal.valueOf(100);
    private static final String JSON_ID = "$.id";

    private static final String PATH_PDF = "/{id}/pdf";
    private static final String PATH_DOCUMENT_TYPE = "/{id}/document-type";
    private static final String INV_NUMBER = "INV-001";

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private PdfInvoiceService pdfInvoiceService;

    @InjectMocks
    private InvoiceController invoiceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private InvoiceResponse invoiceResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(invoiceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        invoiceResponse = new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, INV_NUMBER, null,
                AMOUNT_100, InvoiceStatus.ISSUED,
                RESERVATION_ID, GUEST_ID, null,
                null, List.of(), List.of());
    }

    @Test
    void shouldGetInvoiceByIdReturn200() throws Exception {
        when(invoiceService.getInvoice(INVOICE_ID)).thenReturn(invoiceResponse);

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, INVOICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(INVOICE_ID.toString()))
                .andExpect(jsonPath("$.invoiceNumber").value(INV_NUMBER));
    }

    @Test
    void shouldGetInvoiceByIdReturn404WhenNotFound() throws Exception {
        when(invoiceService.getInvoice(INVOICE_ID))
                .thenThrow(new NotFoundException("INVOICE_NOT_FOUND"));

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, INVOICE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllInvoicesReturn200() throws Exception {
        final Page<InvoiceResponse> page = new PageImpl<>(
                List.of(invoiceResponse), PageRequest.of(0, 20), 1L);
        when(invoiceService.getAllInvoices(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetLatestByReservationReturn200() throws Exception {
        when(invoiceService.getLatestInvoiceByReservation(RESERVATION_ID)).thenReturn(invoiceResponse);

        mockMvc.perform(get(BASE_URL + PATH_RESERVATION_LATEST, RESERVATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(INVOICE_ID.toString()));
    }

    @Test
    void shouldCreateInvoiceForStayReturn201() throws Exception {
        final StayInvoiceRequest request = new StayInvoiceRequest(STAY_ID, GUEST_ID, RESERVATION_ID);
        final InvoiceResponse stayInvoice = new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, "INV-002", null,
                BigDecimal.ZERO, InvoiceStatus.ISSUED,
                RESERVATION_ID, GUEST_ID, STAY_ID,
                null, List.of(), List.of());
        when(invoiceService.createInvoiceForStay(any(StayInvoiceRequest.class))).thenReturn(stayInvoice);

        mockMvc.perform(post(BASE_URL + PATH_STAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stayId").value(STAY_ID.toString()));
    }

    @Test
    void shouldGetPdfReturn200WithApplicationPdf() throws Exception {
        final byte[] pdfBytes = {0x25, 0x50, 0x44, 0x46};  // %PDF magic bytes
        when(pdfInvoiceService.generateInvoicePdf(INVOICE_ID)).thenReturn(pdfBytes);

        mockMvc.perform(get(BASE_URL + PATH_PDF, INVOICE_ID)
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void shouldGetPdfReturn404WhenInvoiceNotFound() throws Exception {
        when(pdfInvoiceService.generateInvoicePdf(INVOICE_ID))
                .thenThrow(new NotFoundException("INVOICE_NOT_FOUND"));

        mockMvc.perform(get(BASE_URL + PATH_PDF, INVOICE_ID)
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateDocumentTypeReturn200() throws Exception {
        final DocumentTypeRequest request = new DocumentTypeRequest(DocumentType.RICEVUTA);
        final InvoiceResponse updated = new InvoiceResponse(
                INVOICE_ID, HOTEL_ID, INV_NUMBER, null,
                AMOUNT_100, InvoiceStatus.ISSUED,
                RESERVATION_ID, GUEST_ID, null,
                DocumentType.RICEVUTA, List.of(), List.of());
        when(invoiceService.updateDocumentType(eq(INVOICE_ID), eq(DocumentType.RICEVUTA))).thenReturn(updated);

        mockMvc.perform(patch(BASE_URL + PATH_DOCUMENT_TYPE, INVOICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("RICEVUTA"));
    }

    @Test
    void shouldAddChargeReturn201() throws Exception {
        final ChargeRequest chargeRequest = new ChargeRequest(
                ChargeType.FB_ORDER, "Espresso x2", BigDecimal.valueOf(6), null);
        final ChargeResponse chargeResponse = new ChargeResponse(
                UUID.randomUUID(), INVOICE_ID, ChargeType.FB_ORDER,
                "Espresso x2", BigDecimal.valueOf(6), null, null, null);
        when(invoiceService.addCharge(eq(STAY_ID), any(ChargeRequest.class))).thenReturn(chargeResponse);

        mockMvc.perform(post(BASE_URL + PATH_STAY_CHARGES, STAY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chargeRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FB_ORDER"));
    }
}
