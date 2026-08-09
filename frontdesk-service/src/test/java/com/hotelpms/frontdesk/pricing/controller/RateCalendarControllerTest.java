package com.hotelpms.frontdesk.pricing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.GlobalExceptionHandler;
import com.hotelpms.frontdesk.pricing.dto.RateBulkApplyRequest;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarDay;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarResponse;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarRow;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;
import com.hotelpms.frontdesk.pricing.service.RateCalendarService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class RateCalendarControllerTest {

    private static final String CALENDAR_URL = "/api/v1/rate-calendar";
    private static final String BULK_APPLY_URL = "/api/v1/rate-calendar/bulk-apply";
    private static final String PRICE_150 = "150.00";
    private static final LocalDate AUG_1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate AUG_31 = LocalDate.of(2026, 8, 31);
    private static final UUID HOTEL_ID = UUID.randomUUID();

    @Mock
    private RateCalendarService rateCalendarService;

    @InjectMocks
    private RateCalendarController rateCalendarController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID roomTypeId;

    @BeforeEach
    void setUp() {
        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testuser", "", List.of());
        auth.setDetails(HOTEL_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(rateCalendarController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        roomTypeId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGetCalendarReturn200() throws Exception {
        final RateCalendarDay day = new RateCalendarDay(AUG_1, new BigDecimal(PRICE_150), UUID.randomUUID(), "Alta");
        final RateCalendarRow row = new RateCalendarRow(roomTypeId, "Double", new BigDecimal("100.00"), List.of(day));
        when(rateCalendarService.getCalendar(HOTEL_ID, AUG_1, AUG_31))
                .thenReturn(new RateCalendarResponse(List.of(row)));

        mockMvc.perform(get(CALENDAR_URL).param("from", AUG_1.toString()).param("to", AUG_31.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].roomTypeName").value("Double"))
                .andExpect(jsonPath("$.rows[0].days[0].price").value(150.00));
    }

    @Test
    void shouldReturn400WhenCalendarRangeIsInvalid() throws Exception {
        when(rateCalendarService.getCalendar(eq(HOTEL_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new BadRequestException("INVALID_DATE_RANGE"));

        mockMvc.perform(get(CALENDAR_URL).param("from", AUG_31.toString()).param("to", AUG_1.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldBulkApplyReturn200() throws Exception {
        final RateSeasonResponse created = new RateSeasonResponse(
                UUID.randomUUID(), roomTypeId, "Alta stagione", AUG_1, AUG_31, new BigDecimal(PRICE_150));
        final RateBulkApplyRequest request = new RateBulkApplyRequest(
                List.of(roomTypeId), AUG_1, AUG_31, new BigDecimal(PRICE_150), "Alta stagione");

        when(rateCalendarService.bulkApply(eq(HOTEL_ID), any(RateBulkApplyRequest.class)))
                .thenReturn(List.of(created));

        mockMvc.perform(post(BULK_APPLY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alta stagione"));
    }

    @Test
    void shouldRejectBulkApplyWithEndDateBeforeStartDateReturn400() throws Exception {
        final RateBulkApplyRequest invalidRequest = new RateBulkApplyRequest(
                List.of(roomTypeId), AUG_31, AUG_1, new BigDecimal(PRICE_150), "Alta stagione");

        mockMvc.perform(post(BULK_APPLY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectBulkApplyWithNoRoomTypesReturn400() throws Exception {
        final RateBulkApplyRequest invalidRequest = new RateBulkApplyRequest(
                List.of(), AUG_1, AUG_31, new BigDecimal(PRICE_150), "Alta stagione");

        mockMvc.perform(post(BULK_APPLY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
