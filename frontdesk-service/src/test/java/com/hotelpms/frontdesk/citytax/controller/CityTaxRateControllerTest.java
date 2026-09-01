package com.hotelpms.frontdesk.citytax.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.frontdesk.citytax.dto.CityTaxApplicabilityRequest;
import com.hotelpms.frontdesk.citytax.dto.CityTaxApplicabilityResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateRequest;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateResponse;
import com.hotelpms.frontdesk.citytax.service.CityTaxRateAdminService;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.GlobalExceptionHandler;
import com.hotelpms.frontdesk.stays.domain.CityTaxApplicability;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class CityTaxRateControllerTest {

    private static final String BASE_URL = "/api/v1/stays/city-tax-rates";
    private static final String CATEGORY = "4_STAR";
    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT_PER_NIGHT = new BigDecimal("2.50");
    private static final int MAX_TAXABLE_NIGHTS = 7;
    private static final int EXEMPT_UNDER_AGE = 14;
    private static final LocalDate VALID_FROM = LocalDate.of(2026, 1, 1);

    @Mock
    private CityTaxRateAdminService cityTaxRateAdminService;

    @Mock
    private HotelSettingsService hotelSettingsService;

    @InjectMocks
    private CityTaxRateController cityTaxRateController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private CityTaxRateResponse response;

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

        mockMvc = MockMvcBuilders.standaloneSetup(cityTaxRateController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        response = new CityTaxRateResponse(UUID.randomUUID(), "099014", CATEGORY,
                AMOUNT_PER_NIGHT, MAX_TAXABLE_NIGHTS, EXEMPT_UNDER_AGE, VALID_FROM, null, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateRuleReturn201() throws Exception {
        final CityTaxRateRequest request = new CityTaxRateRequest(
                CATEGORY, AMOUNT_PER_NIGHT, MAX_TAXABLE_NIGHTS, EXEMPT_UNDER_AGE, VALID_FROM, null);
        when(cityTaxRateAdminService.createRule(eq(HOTEL_ID), any(CityTaxRateRequest.class))).thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value(CATEGORY));
    }

    @Test
    void shouldRejectCreateRuleWithNegativeAmountReturn400() throws Exception {
        final CityTaxRateRequest invalid = new CityTaxRateRequest(CATEGORY, new BigDecimal("-1.00"),
                MAX_TAXABLE_NIGHTS, EXEMPT_UNDER_AGE, VALID_FROM, null);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenComuneNotConfigured() throws Exception {
        final CityTaxRateRequest request = new CityTaxRateRequest(
                CATEGORY, AMOUNT_PER_NIGHT, MAX_TAXABLE_NIGHTS, EXEMPT_UNDER_AGE, VALID_FROM, null);
        when(cityTaxRateAdminService.createRule(eq(HOTEL_ID), any(CityTaxRateRequest.class)))
                .thenThrow(new BadRequestException("CITY_TAX_COMUNE_NOT_CONFIGURED"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409WhenCreateRuleOverlaps() throws Exception {
        final CityTaxRateRequest request = new CityTaxRateRequest(
                CATEGORY, AMOUNT_PER_NIGHT, MAX_TAXABLE_NIGHTS, EXEMPT_UNDER_AGE, VALID_FROM, null);
        when(cityTaxRateAdminService.createRule(eq(HOTEL_ID), any(CityTaxRateRequest.class)))
                .thenThrow(new ConflictException("CITY_TAX_RATE_OVERLAP"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldListRulesReturn200() throws Exception {
        when(cityTaxRateAdminService.listRules(HOTEL_ID)).thenReturn(List.of(response));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value(CATEGORY));
    }

    @Test
    void shouldGetApplicabilityReturn200() throws Exception {
        when(hotelSettingsService.getCityTaxApplicability(HOTEL_ID))
                .thenReturn(new CityTaxApplicabilityResponse(CityTaxApplicability.APPLICABLE));

        mockMvc.perform(get(BASE_URL + "/applicability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicability").value("APPLICABLE"));
    }

    @Test
    void shouldUpdateApplicabilityReturn200() throws Exception {
        when(hotelSettingsService.updateCityTaxApplicability(eq(HOTEL_ID), any(CityTaxApplicabilityRequest.class)))
                .thenReturn(new CityTaxApplicabilityResponse(CityTaxApplicability.NOT_APPLICABLE));

        mockMvc.perform(put(BASE_URL + "/applicability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CityTaxApplicabilityRequest(CityTaxApplicability.NOT_APPLICABLE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicability").value("NOT_APPLICABLE"));
    }
}
