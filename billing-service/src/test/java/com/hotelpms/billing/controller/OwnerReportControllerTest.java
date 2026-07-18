package com.hotelpms.billing.controller;

import com.hotelpms.billing.dto.OwnerFinancialReportDto;
import com.hotelpms.billing.exception.GlobalExceptionHandler;
import com.hotelpms.billing.service.OwnerReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @Mock
    private OwnerReportService ownerReportService;

    @InjectMocks
    private OwnerReportController ownerReportController;

    private MockMvc mockMvc;
    private UUID hotelId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ownerReportController)
                .setControllerAdvice(new GlobalExceptionHandler())
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
                new BigDecimal("1500.00"),
                10L,
                8L,
                List.of());
        when(ownerReportService.getFinancialReport(eq(hotelId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(report);

        mockMvc.perform(get(BASE_URL)
                        .param(PARAM_START, "2026-05-01")
                        .param(PARAM_END, "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoices").value(10))
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
                .andExpect(jsonPath("$.totalInvoices").value(0));
    }
}
