package com.hotelpms.frontdesk.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.frontdesk.dashboard.dto.DaySheetResponse;
import com.hotelpms.frontdesk.dashboard.service.DaySheetService;
import com.hotelpms.frontdesk.exception.GlobalExceptionHandler;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class DaySheetControllerTest {

    private static final String BASE_URL = "/api/v1/frontdesk/day-sheet";
    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);
    private static final long GUESTS_IN_HOUSE = 5L;
    private static final int AVAILABLE_ROOMS = 7;

    @Mock
    private DaySheetService daySheetService;

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

        mockMvc = MockMvcBuilders.standaloneSetup(new DaySheetController(daySheetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnDaySheetForRequestedDateScopedToCallerHotel() throws Exception {
        final DaySheetResponse response = new DaySheetResponse(
                DATE, 3, 2, GUESTS_IN_HOUSE, 4L, AVAILABLE_ROOMS, Map.of(RoomStatus.CLEAN, 10L, RoomStatus.DIRTY, 2L));
        when(daySheetService.getDaySheet(DATE, HOTEL_ID)).thenReturn(response);

        mockMvc.perform(get(BASE_URL).param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DATE.toString()))
                .andExpect(jsonPath("$.todayArrivals").value(3))
                .andExpect(jsonPath("$.todayDepartures").value(2))
                .andExpect(jsonPath("$.guestsInHouse").value(GUESTS_IN_HOUSE))
                .andExpect(jsonPath("$.currentStays").value(4))
                .andExpect(jsonPath("$.availableRooms").value(AVAILABLE_ROOMS))
                .andExpect(jsonPath("$.roomStatusCounts.CLEAN").value(10))
                .andExpect(jsonPath("$.roomStatusCounts.DIRTY").value(2));
    }

    @Test
    void shouldReturn400WhenDateParamMissing() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isBadRequest());
    }
}
