package com.hotelpms.stay.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.stay.dto.HotelSettingsRequest;
import com.hotelpms.stay.dto.HotelSettingsResponse;
import com.hotelpms.stay.exception.GlobalExceptionHandler;
import com.hotelpms.stay.service.HotelSettingsService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HotelSettingsControllerTest {

    private static final String BASE_URL = "/api/v1/stays/settings";
    private static final String JSON_HOTEL_ID = "$.hotelId";
    private static final String HOTEL_NAME = "Test Hotel";
    private static final String HOTEL_ADDRESS = "Via Roma 1";

    @Mock
    private HotelSettingsService hotelSettingsService;

    @InjectMocks
    private HotelSettingsController hotelSettingsController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID hotelId;
    private HotelSettingsResponse settingsResponse;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();

        final UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user", "", List.of());
        auth.setDetails(hotelId.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(hotelSettingsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        settingsResponse = new HotelSettingsResponse(
                hotelId, false, HOTEL_NAME, HOTEL_ADDRESS, null, null, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGetSettingsReturn200() throws Exception {
        when(hotelSettingsService.getOrCreate(hotelId)).thenReturn(settingsResponse);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_HOTEL_ID).value(hotelId.toString()));
    }

    @Test
    void shouldUpdateSettingsReturn200() throws Exception {
        final HotelSettingsRequest request = new HotelSettingsRequest(
                true, HOTEL_NAME, HOTEL_ADDRESS, null, null, null);
        final HotelSettingsResponse updated = new HotelSettingsResponse(
                hotelId, true, HOTEL_NAME, HOTEL_ADDRESS, null, null, null);
        when(hotelSettingsService.update(eq(hotelId), any(HotelSettingsRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_HOTEL_ID).value(hotelId.toString()))
                .andExpect(jsonPath("$.alloggiatiAutoSend").value(true));
    }

    @Test
    void shouldUpdateSettingsReturn200WithAutoSendFalse() throws Exception {
        final HotelSettingsRequest request = new HotelSettingsRequest(
                false, HOTEL_NAME, null, null, null, null);
        when(hotelSettingsService.update(eq(hotelId), any(HotelSettingsRequest.class)))
                .thenReturn(settingsResponse);

        mockMvc.perform(put(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alloggiatiAutoSend").value(false));
    }
}
