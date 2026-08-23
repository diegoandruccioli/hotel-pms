package com.hotelpms.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.billing.dto.KpiPeriodDto;
import com.hotelpms.billing.dto.KpiReportDto;
import com.hotelpms.billing.dto.OwnerFinancialReportDto;
import com.hotelpms.billing.dto.OwnerFinancialSummaryDto;
import com.hotelpms.billing.dto.ReportGranularity;
import com.hotelpms.billing.exception.GlobalExceptionHandler;
import com.hotelpms.billing.service.KpiReportService;
import com.hotelpms.billing.service.OwnerReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OwnerReportControllerTest {

    private static final String BASE_URL = "/api/v1/reports/owner";
    private static final String PARAM_START = "startDate";
    private static final String PARAM_END = "endDate";
    private static final String JSON_TOTAL_INVOICES = "$.totalInvoices";
    private static final String SUMMARY_REVENUE = "1500.00";
    private static final double SUMMARY_REVENUE_VALUE = 1500.00;
    private static final String KPI_URL = "/api/v1/reports/kpi";
    private static final String KPI_DATE = "2026-08-01";
    private static final String GRANULARITY_DAY = "DAY";
    private static final double ADR_VALUE = 125.00;
    private static final double REVPAR_VALUE = 50.00;
    private static final double OCCUPANCY_RATE_VALUE = 0.4;
    private static final long KPI_OCCUPIED_NIGHTS = 4L;

    @Mock
    private OwnerReportService ownerReportService;

    @Mock
    private KpiReportService kpiReportService;

    @InjectMocks
    private OwnerReportController ownerReportController;

    private MockMvc mockMvc;
    private UUID hotelId;

    @BeforeEach
    void setUp() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(ownerReportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        hotelId = UUID.randomUUID();
        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "owner", "", List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        auth.setDetails(hotelId.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGetOwnerReportReturn200() throws Exception {
        final OwnerFinancialReportDto report = new OwnerFinancialReportDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new BigDecimal(SUMMARY_REVENUE),
                10L,
                8L,
                List.of());
        when(ownerReportService.getFinancialReport(eq(hotelId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(report);

        mockMvc.perform(get(BASE_URL)
                        .param(PARAM_START, "2026-05-01")
                        .param(PARAM_END, "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_INVOICES).value(10))
                .andExpect(jsonPath("$.paidInvoices").value(8));

        verify(ownerReportService).getFinancialReport(eq(hotelId), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void shouldGetOwnerReportReturn200WithEmptyPeriod() throws Exception {
        final OwnerFinancialReportDto empty = new OwnerFinancialReportDto(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                BigDecimal.ZERO,
                0L,
                0L,
                List.of());
        when(ownerReportService.getFinancialReport(eq(hotelId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(empty);

        mockMvc.perform(get(BASE_URL)
                        .param(PARAM_START, "2026-04-01")
                        .param(PARAM_END, "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_INVOICES).value(0));
    }

    @Test
    void shouldGetOwnerSummaryReturn200() throws Exception {
        final OwnerFinancialSummaryDto summary = new OwnerFinancialSummaryDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new BigDecimal(SUMMARY_REVENUE),
                10L,
                8L,
                BigDecimal.ZERO);
        when(ownerReportService.getFinancialSummary(eq(hotelId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(summary);

        mockMvc.perform(get(BASE_URL + "/summary")
                        .param(PARAM_START, "2026-05-01")
                        .param(PARAM_END, "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_INVOICES).value(10))
                .andExpect(jsonPath("$.paidInvoices").value(8))
                .andExpect(jsonPath("$.totalRevenue").value(SUMMARY_REVENUE_VALUE));

        verify(ownerReportService).getFinancialSummary(eq(hotelId), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void shouldGetKpiReportReturn200() throws Exception {
        final KpiPeriodDto period = new KpiPeriodDto(
                LocalDate.parse(KPI_DATE), new BigDecimal("500.00"), KPI_OCCUPIED_NIGHTS, 10L,
                BigDecimal.valueOf(ADR_VALUE), BigDecimal.valueOf(REVPAR_VALUE),
                BigDecimal.valueOf(OCCUPANCY_RATE_VALUE));
        final KpiReportDto report = new KpiReportDto(List.of(period), period);
        when(kpiReportService.getKpiReport(eq(hotelId), any(LocalDate.class), any(LocalDate.class),
                eq(ReportGranularity.DAY))).thenReturn(report);

        mockMvc.perform(get(KPI_URL)
                        .param(PARAM_START, KPI_DATE)
                        .param(PARAM_END, KPI_DATE)
                        .param("granularity", GRANULARITY_DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periods[0].adr").value(ADR_VALUE))
                .andExpect(jsonPath("$.periods[0].revpar").value(REVPAR_VALUE))
                .andExpect(jsonPath("$.periods[0].occupancyRate").value(OCCUPANCY_RATE_VALUE))
                .andExpect(jsonPath("$.totals.occupiedRoomNights").value(KPI_OCCUPIED_NIGHTS));

        verify(kpiReportService).getKpiReport(eq(hotelId), any(LocalDate.class), any(LocalDate.class),
                eq(ReportGranularity.DAY));
    }

    @Test
    void shouldReturn400WhenKpiGranularityIsNotAValidEnumValue() throws Exception {
        mockMvc.perform(get(KPI_URL)
                        .param(PARAM_START, KPI_DATE)
                        .param(PARAM_END, KPI_DATE)
                        .param("granularity", "fortnight"))
                .andExpect(status().isBadRequest());
    }
}
