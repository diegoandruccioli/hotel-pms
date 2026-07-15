package com.hotelpms.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.notification.dto.CheckinNotificationRequest;
import com.hotelpms.notification.dto.CheckoutNotificationRequest;
import com.hotelpms.notification.dto.InvoiceLineItemDto;
import com.hotelpms.notification.dto.ReservationConfirmedRequest;
import com.hotelpms.notification.exception.GlobalExceptionHandler;
import com.hotelpms.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String BASE_URL = "/internal/notifications";
    private static final String GUEST_EMAIL = "guest@example.com";
    private static final String HOTEL_NAME = "Grand Hotel";
    private static final String GUEST_NAME = "Jane Smith";
    private static final String ROOM_NUMBER = "201";
    private static final String LOCALE_IT = "it";
    private static final String CURRENCY_EUR = "EUR";

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(notificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void reservationConfirmedValidRequestReturns204() throws Exception {
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                GUEST_EMAIL, "John Doe", HOTEL_NAME, "Superior Room",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), 4, "RES-001", LOCALE_IT);

        mockMvc.perform(post(BASE_URL + "/reservation-confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(notificationService, times(1)).sendReservationConfirmed(any());
    }

    @Test
    void reservationConfirmedMissingEmailReturns400() throws Exception {
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                null, "John Doe", HOTEL_NAME, "Room",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), 4, "RES-001", LOCALE_IT);

        mockMvc.perform(post(BASE_URL + "/reservation-confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkinValidRequestReturns204() throws Exception {
        final CheckinNotificationRequest req = new CheckinNotificationRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, ROOM_NUMBER,
                LocalDate.of(2026, 8, 5), LOCALE_IT);

        mockMvc.perform(post(BASE_URL + "/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(notificationService, times(1)).sendCheckin(any());
    }

    @Test
    void checkoutValidRequestReturns204() throws Exception {
        final CheckoutNotificationRequest req = new CheckoutNotificationRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, ROOM_NUMBER,
                LocalDateTime.of(2026, 8, 1, 14, 0),
                LocalDateTime.of(2026, 8, 5, 11, 0),
                List.of(new InvoiceLineItemDto("Room charge", BigDecimal.valueOf(400))),
                BigDecimal.valueOf(400), CURRENCY_EUR, LOCALE_IT);

        mockMvc.perform(post(BASE_URL + "/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(notificationService, times(1)).sendCheckout(any());
    }

    @Test
    void checkoutMissingGuestEmailReturns400() throws Exception {
        final CheckoutNotificationRequest req = new CheckoutNotificationRequest(
                null, GUEST_NAME, HOTEL_NAME, ROOM_NUMBER,
                LocalDateTime.of(2026, 8, 1, 14, 0),
                LocalDateTime.of(2026, 8, 5, 11, 0),
                List.of(), BigDecimal.valueOf(400), CURRENCY_EUR, LOCALE_IT);

        mockMvc.perform(post(BASE_URL + "/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
