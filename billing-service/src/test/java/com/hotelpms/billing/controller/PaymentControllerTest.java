package com.hotelpms.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.billing.domain.PaymentMethod;
import com.hotelpms.billing.dto.PaymentRequest;
import com.hotelpms.billing.dto.PaymentResponse;
import com.hotelpms.billing.exception.GlobalExceptionHandler;
import com.hotelpms.billing.exception.NotFoundException;
import com.hotelpms.billing.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final String BASE_URL = "/api/v1/invoices/{invoiceId}/payments";
    private static final String TXN_REF = "TXN-12345";
    private static final BigDecimal AMOUNT_100 = BigDecimal.valueOf(100);
    private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private PaymentResponse paymentResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        paymentResponse = new PaymentResponse(
                PAYMENT_ID, LocalDateTime.now(), AMOUNT_100,
                PaymentMethod.CREDIT_CARD, TXN_REF, INVOICE_ID);
    }

    @Test
    void shouldAddPaymentReturn201() throws Exception {
        final PaymentRequest request = new PaymentRequest(AMOUNT_100, PaymentMethod.CREDIT_CARD, TXN_REF);
        when(paymentService.addPayment(eq(INVOICE_ID), any(PaymentRequest.class))).thenReturn(paymentResponse);

        mockMvc.perform(post(BASE_URL, INVOICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.transactionReference").value(TXN_REF));
    }

    @Test
    void shouldAddPaymentReturn201WhenTransactionReferenceIsAbsent() throws Exception {
        final PaymentRequest request = new PaymentRequest(AMOUNT_100, PaymentMethod.CASH, null);
        final PaymentResponse cashResponse = new PaymentResponse(
                PAYMENT_ID, LocalDateTime.now(), AMOUNT_100, PaymentMethod.CASH, null, INVOICE_ID);
        when(paymentService.addPayment(eq(INVOICE_ID), any(PaymentRequest.class))).thenReturn(cashResponse);

        mockMvc.perform(post(BASE_URL, INVOICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAddPaymentReturn400WhenAmountIsNull() throws Exception {
        final String body = "{\"paymentMethod\":\"CREDIT_CARD\",\"transactionReference\":\"TXN-001\"}";

        mockMvc.perform(post(BASE_URL, INVOICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAddPaymentReturn400WhenMethodIsNull() throws Exception {
        final String body = "{\"amount\":100,\"transactionReference\":\"TXN-001\"}";

        mockMvc.perform(post(BASE_URL, INVOICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAddPaymentReturn404WhenInvoiceNotFound() throws Exception {
        final PaymentRequest request = new PaymentRequest(AMOUNT_100, PaymentMethod.CREDIT_CARD, TXN_REF);
        when(paymentService.addPayment(eq(INVOICE_ID), any(PaymentRequest.class)))
                .thenThrow(new NotFoundException("INVOICE_NOT_FOUND"));

        mockMvc.perform(post(BASE_URL, INVOICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
