package com.hotelpms.frontdesk.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.frontdesk.dashboard.dto.OccupancyPeriodResponse;
import com.hotelpms.frontdesk.dashboard.dto.OccupancySummaryResponse;
import com.hotelpms.frontdesk.dashboard.dto.ReportGranularity;
import com.hotelpms.frontdesk.dashboard.service.OccupancySummaryService;
import com.hotelpms.frontdesk.exception.GlobalExceptionHandler;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class OccupancySummaryControllerTest {

    private static final String BASE_URL = "/api/v1/frontdesk/occupancy-summary";
    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate DATE_FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 9, 1);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 8, 3);
    private static final int TOTAL_ROOMS = 12;
    private static final long OCCUPIED_NIGHTS = 20L;

    @Mock
    private OccupancySummaryService occupancySummaryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "owner1", "", List.of());
        auth.setDetails(HOTEL_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);

        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new OccupancySummaryController(occupancySummaryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnOccupancySummaryForRequestedRangeScopedToCallerHotel() throws Exception {
        final OccupancySummaryResponse response = new OccupancySummaryResponse(
                TOTAL_ROOMS, List.of(new OccupancyPeriodResponse(PERIOD_START, OCCUPIED_NIGHTS)));
        when(occupancySummaryService.getOccupancySummary(DATE_FROM, DATE_TO, ReportGranularity.WEEK, HOTEL_ID))
                .thenReturn(response);

        mockMvc.perform(get(BASE_URL)
                        .param("dateFrom", DATE_FROM.toString())
                        .param("dateTo", DATE_TO.toString())
                        .param("granularity", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRooms").value(TOTAL_ROOMS))
                .andExpect(jsonPath("$.periods[0].periodStart").value(PERIOD_START.toString()))
                .andExpect(jsonPath("$.periods[0].occupiedRoomNights").value(OCCUPIED_NIGHTS));
    }

    @Test
    void shouldReturn400WhenGranularityIsNotAValidEnumValue() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .param("dateFrom", DATE_FROM.toString())
                        .param("dateTo", DATE_TO.toString())
                        .param("granularity", "fortnight"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenRequiredParamsAreMissing() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isBadRequest());
    }
}
