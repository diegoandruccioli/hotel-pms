package com.hotelpms.frontdesk.nightaudit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.GlobalExceptionHandler;
import com.hotelpms.frontdesk.nightaudit.dto.DaySheetTrendPoint;
import com.hotelpms.frontdesk.nightaudit.dto.DaySheetTrendResponse;
import com.hotelpms.frontdesk.nightaudit.service.DaySheetTrendService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class DaySheetTrendControllerTest {

    private static final String URL = "/api/v1/frontdesk/day-sheet/trend";
    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
    private static final int DEFAULT_DAYS = 7;
    private static final long GUESTS_IN_HOUSE = 21L;
    private static final int ARRIVALS = 5;
    private static final int AVAILABLE_ROOMS = 9;
    private static final int TOO_MANY_DAYS = 15;
    private static final String PARAM_DATE = "date";
    private static final String PARAM_DAYS = "days";

    @Mock
    private DaySheetTrendService daySheetTrendService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testuser", "", List.of());
        auth.setDetails(HOTEL_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);

        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new DaySheetTrendController(daySheetTrendService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheSnapshotsScopedToTheCallersHotel() throws Exception {
        final LocalDate day = DATE.minusDays(1);
        when(daySheetTrendService.getTrend(DATE, 2, HOTEL_ID)).thenReturn(new DaySheetTrendResponse(
                DATE.minusDays(2), day, List.of(new DaySheetTrendPoint(day, ARRIVALS, 4, GUESTS_IN_HOUSE, AVAILABLE_ROOMS))));

        mockMvc.perform(get(URL).param(PARAM_DATE, DATE.toString()).param(PARAM_DAYS, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(DATE.minusDays(2).toString()))
                .andExpect(jsonPath("$.to").value(day.toString()))
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0].date").value(day.toString()))
                .andExpect(jsonPath("$.points[0].arrivals").value(ARRIVALS))
                .andExpect(jsonPath("$.points[0].departures").value(4))
                .andExpect(jsonPath("$.points[0].guestsInHouse").value(GUESTS_IN_HOUSE))
                .andExpect(jsonPath("$.points[0].availableRooms").value(AVAILABLE_ROOMS));
    }

    @Test
    void defaultsToSevenDaysWhenDaysIsOmitted() throws Exception {
        when(daySheetTrendService.getTrend(DATE, DEFAULT_DAYS, HOTEL_ID)).thenReturn(
                new DaySheetTrendResponse(DATE.minusDays(DEFAULT_DAYS), DATE.minusDays(1), List.of()));

        mockMvc.perform(get(URL).param(PARAM_DATE, DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    void returns400WhenTheServiceRejectsTheWindow() throws Exception {
        when(daySheetTrendService.getTrend(DATE, TOO_MANY_DAYS, HOTEL_ID))
                .thenThrow(new BadRequestException("DAY_SHEET_TREND_DAYS_OUT_OF_RANGE"));

        mockMvc.perform(get(URL).param(PARAM_DATE, DATE.toString()).param(PARAM_DAYS, String.valueOf(TOO_MANY_DAYS)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenDateParamMissing() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(daySheetTrendService);
    }

    @Test
    void returns400WhenDaysIsNotANumber() throws Exception {
        mockMvc.perform(get(URL).param(PARAM_DATE, DATE.toString()).param(PARAM_DAYS, "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(daySheetTrendService);
    }

    @Test
    void returns400WhenDateIsMalformed() throws Exception {
        mockMvc.perform(get(URL).param(PARAM_DATE, "04-10-2026"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(daySheetTrendService);
    }
}
