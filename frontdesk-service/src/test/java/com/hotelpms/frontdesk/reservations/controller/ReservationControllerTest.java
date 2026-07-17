package com.hotelpms.frontdesk.reservations.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.dto.ReservationLineItemRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationRequest;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import com.hotelpms.frontdesk.reservations.dto.ReservationStatusUpdateRequest;
import com.hotelpms.frontdesk.exception.GlobalExceptionHandler;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    private static final String BASE_URL = "/api/v1/reservations";
    private static final String PATH_BY_ID = "/{id}";
    private static final String PATH_STATUS = "/{id}/status-and-guests";
    private static final String JSON_ID = "$.id";
    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final String FULL_NAME = "Test Guest";

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationController reservationController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID reservationId;
    private UUID roomId;
    private ReservationResponse response;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(reservationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        reservationId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        response = new ReservationResponse(
                reservationId, GUEST_ID, FULL_NAME, 2, 0,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.CONFIRMED, null, true, null, null, false, null);
    }

    @Test
    void shouldCreateReservationReturn201() throws Exception {
        final ReservationRequest request = buildValidRequest();
        when(reservationService.createReservation(any(ReservationRequest.class))).thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(JSON_ID).value(reservationId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(reservationService).createReservation(any(ReservationRequest.class));
    }

    @Test
    void shouldGetReservationByIdReturn200() throws Exception {
        when(reservationService.getReservationById(reservationId)).thenReturn(response);

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(reservationId.toString()))
                .andExpect(jsonPath("$.guestFullName").value(FULL_NAME));
    }

    @Test
    void shouldGetReservationByIdReturn404WhenNotFound() throws Exception {
        when(reservationService.getReservationById(reservationId))
                .thenThrow(new NotFoundException("RESERVATION_NOT_FOUND"));

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, reservationId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllReservationsReturn200() throws Exception {
        final Page<ReservationResponse> page = new PageImpl<>(
                List.of(response), PageRequest.of(0, 20), 1L);
        when(reservationService.getAllReservations(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateReservationReturn200() throws Exception {
        final ReservationRequest request = buildValidRequest();
        when(reservationService.updateReservation(any(UUID.class), any(ReservationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put(BASE_URL + PATH_BY_ID, reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(reservationId.toString()));

        verify(reservationService).updateReservation(any(UUID.class), any(ReservationRequest.class));
    }

    @Test
    void shouldDeleteReservationReturn204() throws Exception {
        doNothing().when(reservationService).deleteReservation(reservationId);

        mockMvc.perform(delete(BASE_URL + PATH_BY_ID, reservationId))
                .andExpect(status().isNoContent());

        verify(reservationService).deleteReservation(reservationId);
    }

    @Test
    void shouldUpdateStatusAndGuestsReturn200() throws Exception {
        final ReservationStatusUpdateRequest statusRequest =
                new ReservationStatusUpdateRequest(ReservationStatus.CHECKED_IN, 2);
        final ReservationResponse checkedInResponse = new ReservationResponse(
                reservationId, GUEST_ID, FULL_NAME, 2, 2,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                ReservationStatus.CHECKED_IN, null, true, null, null, false, null);

        when(reservationService.updateStatusAndGuests(
                any(UUID.class), any(ReservationStatus.class), any(Integer.class)))
                .thenReturn(checkedInResponse);

        mockMvc.perform(patch(BASE_URL + PATH_STATUS, reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
    }

    @Test
    void shouldReturnValidationErrorWhenStatusIsNull() throws Exception {
        final String body = "{\"status\": null, \"actualGuests\": 2}";

        mockMvc.perform(patch(BASE_URL + PATH_STATUS, reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnTrueWhenGuestHasActiveReservations() throws Exception {
        when(reservationService.hasActiveReservations(GUEST_ID)).thenReturn(true);

        mockMvc.perform(get(BASE_URL + "/guest/{guestId}/active", GUEST_ID))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void shouldReturnFalseWhenGuestHasNoActiveReservations() throws Exception {
        when(reservationService.hasActiveReservations(GUEST_ID)).thenReturn(false);

        mockMvc.perform(get(BASE_URL + "/guest/{guestId}/active", GUEST_ID))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void shouldRetryConfirmationEmailReturn200() throws Exception {
        when(reservationService.retryConfirmationEmail(reservationId)).thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/{id}/confirmation-email/retry", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(reservationId.toString()));
    }

    @Test
    void shouldRetryConfirmationEmailReturn404WhenReservationNotFound() throws Exception {
        when(reservationService.retryConfirmationEmail(reservationId))
                .thenThrow(new NotFoundException("RESERVATION_NOT_FOUND"));

        mockMvc.perform(post(BASE_URL + "/{id}/confirmation-email/retry", reservationId))
                .andExpect(status().isNotFound());
    }

    private ReservationRequest buildValidRequest() {
        final ReservationLineItemRequest lineItem =
                new ReservationLineItemRequest(roomId, BigDecimal.valueOf(100));
        return new ReservationRequest(
                GUEST_ID, 2,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                ReservationStatus.CONFIRMED,
                List.of(lineItem));
    }
}
