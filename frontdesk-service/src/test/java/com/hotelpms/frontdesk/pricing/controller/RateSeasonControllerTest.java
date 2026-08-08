package com.hotelpms.frontdesk.pricing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.GlobalExceptionHandler;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonRequest;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;
import com.hotelpms.frontdesk.pricing.service.RateSeasonAdminService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class RateSeasonControllerTest {

    private static final String BASE_URL = "/api/v1/room-types/{roomTypeId}/rate-seasons";
    private static final String PATH_BY_ID = "/{id}";
    private static final String JSON_NAME = "$.name";
    private static final String SEASON_NAME = "Alta stagione";
    private static final String PRICE_150 = "150.00";
    private static final LocalDate SEASON_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEASON_END = LocalDate.of(2026, 8, 31);
    private static final UUID HOTEL_ID = UUID.randomUUID();

    @Mock
    private RateSeasonAdminService rateSeasonAdminService;

    @InjectMocks
    private RateSeasonController rateSeasonController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID roomTypeId;
    private UUID seasonId;
    private RateSeasonResponse seasonResponse;

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

        mockMvc = MockMvcBuilders.standaloneSetup(rateSeasonController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        roomTypeId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        seasonResponse = new RateSeasonResponse(seasonId, roomTypeId, SEASON_NAME,
                SEASON_START, SEASON_END, new BigDecimal(PRICE_150));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateSeasonReturn201() throws Exception {
        final RateSeasonRequest request = new RateSeasonRequest(SEASON_NAME,
                SEASON_START, SEASON_END, new BigDecimal(PRICE_150));

        when(rateSeasonAdminService.createSeason(eq(roomTypeId), eq(HOTEL_ID), any(RateSeasonRequest.class)))
                .thenReturn(seasonResponse);

        mockMvc.perform(post(BASE_URL, roomTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(seasonId.toString()))
                .andExpect(jsonPath(JSON_NAME).value(SEASON_NAME));

        verify(rateSeasonAdminService).createSeason(eq(roomTypeId), eq(HOTEL_ID), any(RateSeasonRequest.class));
    }

    @Test
    void shouldRejectCreateSeasonWithEndDateBeforeStartDateReturn400() throws Exception {
        final RateSeasonRequest invalidRequest = new RateSeasonRequest(SEASON_NAME,
                SEASON_END, SEASON_START, new BigDecimal(PRICE_150));

        mockMvc.perform(post(BASE_URL, roomTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409WhenCreateSeasonOverlaps() throws Exception {
        final RateSeasonRequest request = new RateSeasonRequest(SEASON_NAME,
                SEASON_START, SEASON_END, new BigDecimal(PRICE_150));

        when(rateSeasonAdminService.createSeason(eq(roomTypeId), eq(HOTEL_ID), any(RateSeasonRequest.class)))
                .thenThrow(new ConflictException("RATE_SEASON_OVERLAP"));

        mockMvc.perform(post(BASE_URL, roomTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldListSeasonsReturn200() throws Exception {
        when(rateSeasonAdminService.listSeasons(roomTypeId, HOTEL_ID)).thenReturn(List.of(seasonResponse));

        mockMvc.perform(get(BASE_URL, roomTypeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(SEASON_NAME));
    }

    @Test
    void shouldUpdateSeasonReturn200() throws Exception {
        final RateSeasonRequest request = new RateSeasonRequest(SEASON_NAME,
                SEASON_START, SEASON_END, new BigDecimal(PRICE_150));

        when(rateSeasonAdminService.updateSeason(eq(seasonId), eq(HOTEL_ID), any(RateSeasonRequest.class)))
                .thenReturn(seasonResponse);

        mockMvc.perform(put(BASE_URL + PATH_BY_ID, roomTypeId, seasonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_NAME).value(SEASON_NAME));
    }

    @Test
    void shouldReturn404WhenUpdateSeasonNotFound() throws Exception {
        final RateSeasonRequest request = new RateSeasonRequest(SEASON_NAME,
                SEASON_START, SEASON_END, new BigDecimal(PRICE_150));

        when(rateSeasonAdminService.updateSeason(eq(seasonId), eq(HOTEL_ID), any(RateSeasonRequest.class)))
                .thenThrow(new NotFoundException("RATE_SEASON_NOT_FOUND"));

        mockMvc.perform(put(BASE_URL + PATH_BY_ID, roomTypeId, seasonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteSeasonReturn204() throws Exception {
        doNothing().when(rateSeasonAdminService).deleteSeason(seasonId, HOTEL_ID);

        mockMvc.perform(delete(BASE_URL + PATH_BY_ID, roomTypeId, seasonId))
                .andExpect(status().isNoContent());

        verify(rateSeasonAdminService).deleteSeason(seasonId, HOTEL_ID);
    }
}
