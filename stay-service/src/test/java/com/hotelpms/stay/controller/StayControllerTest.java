package com.hotelpms.stay.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.stay.domain.StayStatus;
import com.hotelpms.stay.dto.AlloggiatiRowDto;
import com.hotelpms.stay.dto.StayRequest;
import com.hotelpms.stay.dto.StayResponse;
import com.hotelpms.stay.exception.BillingNotPaidException;
import com.hotelpms.stay.exception.GlobalExceptionHandler;
import com.hotelpms.stay.exception.NotFoundException;
import com.hotelpms.stay.service.AlloggiatiReportService;
import com.hotelpms.stay.service.AlloggiatiWebSenderService;
import com.hotelpms.stay.service.StayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class StayControllerTest {

    private static final String BASE_URL = "/api/v1/stays";
    private static final String PATH_BY_ID = "/{id}";
    private static final String PATH_CHECKOUT = "/{id}/check-out";
    private static final String PATH_GUEST_LATEST = "/guest/{guestId}/latest";
    private static final String PATH_REPORT_TXT = "/reports/alloggiati";
    private static final String PATH_REPORT_JSON = "/reports/alloggiati/json";
    private static final String PATH_REPORT_SUBMIT = "/reports/alloggiati/submit";
    private static final String PARAM_DATE = "date";
    private static final String PARAM_RESERVATION = "reservationId";
    private static final String JSON_ID = "$.id";
    private static final String JSON_STATUS = "$.status";
    private static final String TEST_DATE = "2026-05-01";
    private static final String STATO_CODE = "Z000";

    @Mock
    private StayService stayService;

    @Mock
    private AlloggiatiReportService alloggiatiReportService;

    @Mock
    private AlloggiatiWebSenderService alloggiatiWebSenderService;

    @InjectMocks
    private StayController stayController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID stayId;
    private UUID guestId;
    private UUID hotelId;
    private StayResponse stayResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(stayController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        stayId = UUID.randomUUID();
        guestId = UUID.randomUUID();
        hotelId = UUID.randomUUID();

        stayResponse = new StayResponse(
                stayId, hotelId, null, guestId, UUID.randomUUID(),
                StayStatus.CHECKED_IN, null, null, null, null, null, false, List.of(), null, null);
    }

    @Test
    void shouldCheckInReturn201() throws Exception {
        final StayRequest request = new StayRequest(
                hotelId, null, guestId, UUID.randomUUID(),
                StayStatus.CHECKED_IN, LocalDate.now().plusDays(2),
                null, null, List.of());
        when(stayService.checkIn(any(StayRequest.class))).thenReturn(stayResponse);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(JSON_ID).value(stayId.toString()));
    }

    @Test
    void shouldCheckInReturn400WhenGuestIdIsNull() throws Exception {
        final String body = "{\"hotelId\":\"" + hotelId + "\",\"guestId\":null,"
                + "\"roomId\":\"" + UUID.randomUUID() + "\","
                + "\"status\":\"CHECKED_IN\",\"guests\":[]}";

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCheckOutReturn200() throws Exception {
        final StayResponse checkedOut = new StayResponse(
                stayId, hotelId, null, guestId, UUID.randomUUID(),
                StayStatus.CHECKED_OUT, null, null, null, null, null, false, List.of(), null, null);
        when(stayService.checkOut(stayId)).thenReturn(checkedOut);

        mockMvc.perform(put(BASE_URL + PATH_CHECKOUT, stayId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_STATUS).value("CHECKED_OUT"));
    }

    @Test
    void shouldCheckOutReturn404WhenStayNotFound() throws Exception {
        when(stayService.checkOut(stayId))
                .thenThrow(new NotFoundException("STAY_NOT_FOUND"));

        mockMvc.perform(put(BASE_URL + PATH_CHECKOUT, stayId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCheckOutReturn409WhenBillingNotPaid() throws Exception {
        when(stayService.checkOut(stayId))
                .thenThrow(new BillingNotPaidException("BILLING_NOT_PAID"));

        mockMvc.perform(put(BASE_URL + PATH_CHECKOUT, stayId))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldGetStayByIdReturn200() throws Exception {
        when(stayService.getStayById(stayId)).thenReturn(stayResponse);

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, stayId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(stayId.toString()));
    }

    @Test
    void shouldGetStayByIdReturn404WhenNotFound() throws Exception {
        when(stayService.getStayById(stayId))
                .thenThrow(new NotFoundException("STAY_NOT_FOUND"));

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, stayId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllStaysReturn200() throws Exception {
        final Page<StayResponse> page = new PageImpl<>(
                List.of(stayResponse), PageRequest.of(0, 20), 1L);
        when(stayService.getAllStays(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetAllStaysByReservationReturn200() throws Exception {
        final UUID reservationId = UUID.randomUUID();
        final Page<StayResponse> page = new PageImpl<>(
                List.of(stayResponse), PageRequest.of(0, 20), 1L);
        when(stayService.getStaysByReservationId(eq(reservationId), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_URL).param(PARAM_RESERVATION, reservationId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetLastCompletedStayReturn200WhenPresent() throws Exception {
        when(stayService.getLastCompletedStayForGuest(guestId))
                .thenReturn(Optional.of(stayResponse));

        mockMvc.perform(get(BASE_URL + PATH_GUEST_LATEST, guestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(stayId.toString()));
    }

    @Test
    void shouldGetLastCompletedStayReturn204WhenAbsent() throws Exception {
        when(stayService.getLastCompletedStayForGuest(guestId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get(BASE_URL + PATH_GUEST_LATEST, guestId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldDownloadAlloggiatiTxtReturn200WithContentDisposition() throws Exception {
        when(alloggiatiReportService.generateReport(any(LocalDate.class)))
                .thenReturn("16|01/05/2026|1|Rossi|Mario|1|01/01/1980||||Z000|Z000|PASSE|AB12345|Z000");

        mockMvc.perform(get(BASE_URL + PATH_REPORT_TXT)
                        .param(PARAM_DATE, TEST_DATE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("alloggiati-2026-05-01.txt")))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN));
    }

    @Test
    void shouldDownloadAlloggiatiJsonReturn200() throws Exception {
        final AlloggiatiRowDto row = new AlloggiatiRowDto(
                "16", "01/05/2026", 1, "Rossi", "Mario", "1",
                "01/01/1980", "", "", STATO_CODE, STATO_CODE, "PASSE", "AB12345", STATO_CODE);
        when(alloggiatiReportService.generateJsonReport(any(LocalDate.class)))
                .thenReturn(List.of(row));

        mockMvc.perform(get(BASE_URL + PATH_REPORT_JSON)
                        .param(PARAM_DATE, TEST_DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cognome").value("Rossi"));
    }

    @Test
    void shouldSubmitAlloggiatiReportReturn200() throws Exception {
        doNothing().when(alloggiatiWebSenderService).submitReport(any(LocalDate.class));

        mockMvc.perform(post(BASE_URL + PATH_REPORT_SUBMIT)
                        .param(PARAM_DATE, TEST_DATE))
                .andExpect(status().isOk());
    }
}
