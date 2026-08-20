package com.hotelpms.frontdesk.citytax.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryRequest;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryResponse;
import com.hotelpms.frontdesk.citytax.service.HotelCategoryHistoryService;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.GlobalExceptionHandler;
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
class HotelCategoryHistoryControllerTest {

    private static final String BASE_URL = "/api/v1/stays/hotel-category";
    private static final String CATEGORY = "4_STAR";
    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate VALID_FROM = LocalDate.of(2026, 6, 1);

    @Mock
    private HotelCategoryHistoryService hotelCategoryHistoryService;

    @InjectMocks
    private HotelCategoryHistoryController hotelCategoryHistoryController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private HotelCategoryHistoryResponse response;

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

        mockMvc = MockMvcBuilders.standaloneSetup(hotelCategoryHistoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        response = new HotelCategoryHistoryResponse(UUID.randomUUID(), CATEGORY, VALID_FROM, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRecordCategoryReturn201() throws Exception {
        final HotelCategoryHistoryRequest request = new HotelCategoryHistoryRequest(CATEGORY, VALID_FROM);
        when(hotelCategoryHistoryService.recordCategory(eq(HOTEL_ID), any(HotelCategoryHistoryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value(CATEGORY));
    }

    @Test
    void shouldRejectRecordCategoryWithBlankCategoryReturn400() throws Exception {
        final HotelCategoryHistoryRequest invalid = new HotelCategoryHistoryRequest(" ", VALID_FROM);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409WhenRecordCategoryOverlaps() throws Exception {
        final HotelCategoryHistoryRequest request = new HotelCategoryHistoryRequest(CATEGORY, VALID_FROM);
        when(hotelCategoryHistoryService.recordCategory(eq(HOTEL_ID), any(HotelCategoryHistoryRequest.class)))
                .thenThrow(new ConflictException("HOTEL_CATEGORY_OVERLAP"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldListHistoryReturn200() throws Exception {
        when(hotelCategoryHistoryService.listHistory(HOTEL_ID)).thenReturn(List.of(response));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value(CATEGORY));
    }
}
