package com.hotelpms.frontdesk.quotations.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.GlobalExceptionHandler;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.quotations.domain.QuotationStatus;
import com.hotelpms.frontdesk.quotations.dto.ConvertQuotationRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationOptionRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationRequest;
import com.hotelpms.frontdesk.quotations.dto.QuotationResponse;
import com.hotelpms.frontdesk.quotations.service.QuotationService;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class QuotationControllerTest {

    private static final String BASE_URL = "/api/v1/quotations";
    private static final String PATH_BY_ID = "/{id}";
    private static final String JSON_ID = "$.id";
    private static final String CONVERT_PATH = "/{id}/convert";
    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);
    private static final LocalDate CHECK_OUT = LocalDate.now().plusDays(13);
    private static final LocalDate VALID_UNTIL = LocalDate.now().plusDays(5);
    private static final BigDecimal TOTAL_PRICE = BigDecimal.valueOf(300);

    @Mock
    private QuotationService quotationService;

    @InjectMocks
    private QuotationController quotationController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID quotationId;
    private QuotationResponse quotationResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(quotationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        quotationId = UUID.randomUUID();
        quotationResponse = new QuotationResponse(quotationId, GUEST_ID, "Mario Rossi", null,
                CHECK_IN, CHECK_OUT, 2, QuotationStatus.DRAFT, VALID_UNTIL, TOTAL_PRICE,
                List.of(), null, false, null, null, null);
    }

    private static QuotationRequest validRequest() {
        return new QuotationRequest(GUEST_ID, null, null, null,
                CHECK_IN, CHECK_OUT, 2, List.of(new QuotationOptionRequest("Opzione 1", List.of(ROOM_ID))), VALID_UNTIL);
    }

    @Test
    void shouldCreateQuotationReturn201() throws Exception {
        when(quotationService.createQuotation(any(QuotationRequest.class))).thenReturn(quotationResponse);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(JSON_ID).value(quotationId.toString()));
    }

    @Test
    void shouldRejectQuotationWithoutGuestOrProspectReturn400() throws Exception {
        final QuotationRequest invalid = new QuotationRequest(null, null, null, null,
                CHECK_IN, CHECK_OUT, 2, List.of(new QuotationOptionRequest("Opzione 1", List.of(ROOM_ID))), VALID_UNTIL);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectQuotationWithNoOptionsReturn400() throws Exception {
        final QuotationRequest invalid = new QuotationRequest(GUEST_ID, null, null, null,
                CHECK_IN, CHECK_OUT, 2, List.of(), VALID_UNTIL);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateQuotationReturn200() throws Exception {
        when(quotationService.updateQuotation(eq(quotationId), any(QuotationRequest.class))).thenReturn(quotationResponse);

        mockMvc.perform(put(BASE_URL + PATH_BY_ID, quotationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(quotationId.toString()));
    }

    @Test
    void shouldReturn409WhenUpdatingANonDraftQuotation() throws Exception {
        when(quotationService.updateQuotation(eq(quotationId), any(QuotationRequest.class)))
                .thenThrow(new ConflictException("QUOTATION_NOT_EDITABLE"));

        mockMvc.perform(put(BASE_URL + PATH_BY_ID, quotationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldDuplicateQuotationReturn201() throws Exception {
        when(quotationService.duplicateQuotation(quotationId)).thenReturn(quotationResponse);

        mockMvc.perform(post(BASE_URL + "/{id}/duplicate", quotationId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(JSON_ID).value(quotationId.toString()));
    }

    @Test
    void shouldGetQuotationByIdReturn200() throws Exception {
        when(quotationService.getQuotationById(quotationId)).thenReturn(quotationResponse);

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, quotationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(quotationId.toString()));
    }

    @Test
    void shouldReturn404WhenQuotationNotFound() throws Exception {
        when(quotationService.getQuotationById(quotationId)).thenThrow(new NotFoundException("QUOTATION_NOT_FOUND"));

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, quotationId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSendQuotationReturn200() throws Exception {
        when(quotationService.sendQuotationEmail(quotationId)).thenReturn(quotationResponse);

        mockMvc.perform(post(BASE_URL + "/{id}/send", quotationId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn409WhenSendingADeclinedQuotation() throws Exception {
        when(quotationService.sendQuotationEmail(quotationId)).thenThrow(new ConflictException("QUOTATION_DECLINED"));

        mockMvc.perform(post(BASE_URL + "/{id}/send", quotationId))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldConvertToReservationWithNoBodyReturn200() throws Exception {
        final ReservationResponse reservationResponse = new ReservationResponse(UUID.randomUUID(), GUEST_ID, null,
                2, 0, CHECK_IN, CHECK_OUT, ReservationStatus.CONFIRMED, null, true, null, null, false, null);
        when(quotationService.convertToReservation(eq(quotationId), isNull())).thenReturn(reservationResponse);

        mockMvc.perform(post(BASE_URL + CONVERT_PATH, quotationId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldConvertToReservationWithExplicitOptionIdReturn200() throws Exception {
        final UUID optionId = UUID.randomUUID();
        final ReservationResponse reservationResponse = new ReservationResponse(UUID.randomUUID(), GUEST_ID, null,
                2, 0, CHECK_IN, CHECK_OUT, ReservationStatus.CONFIRMED, null, true, null, null, false, null);
        when(quotationService.convertToReservation(quotationId, optionId)).thenReturn(reservationResponse);

        mockMvc.perform(post(BASE_URL + CONVERT_PATH, quotationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConvertQuotationRequest(optionId))))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400WhenConvertingWithoutChoosingAmongMultipleOptions() throws Exception {
        when(quotationService.convertToReservation(eq(quotationId), isNull()))
                .thenThrow(new BadRequestException("QUOTATION_OPTION_REQUIRED"));

        mockMvc.perform(post(BASE_URL + CONVERT_PATH, quotationId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409WhenConvertingAnExpiredQuotation() throws Exception {
        when(quotationService.convertToReservation(eq(quotationId), isNull()))
                .thenThrow(new ConflictException("QUOTATION_EXPIRED"));

        mockMvc.perform(post(BASE_URL + CONVERT_PATH, quotationId))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldDeleteQuotationReturn204() throws Exception {
        doNothing().when(quotationService).deleteQuotation(quotationId);

        mockMvc.perform(delete(BASE_URL + PATH_BY_ID, quotationId))
                .andExpect(status().isNoContent());
    }
}
