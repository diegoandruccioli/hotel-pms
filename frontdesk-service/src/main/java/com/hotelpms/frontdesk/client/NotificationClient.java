package com.hotelpms.frontdesk.client;

import com.hotelpms.frontdesk.client.dto.NotificationCheckinRequest;
import com.hotelpms.frontdesk.client.dto.NotificationCheckoutRequest;
import com.hotelpms.frontdesk.client.dto.NotificationReservationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OpenFeign client for the notification-service transactional email endpoints.
 * All methods have circuit-breaker fallbacks that log silently — notification
 * failures must never block the calling business transaction.
 */
@FeignClient(name = "notification-service")
public interface NotificationClient {

    Logger LOG = LoggerFactory.getLogger(NotificationClient.class);

    /**
     * Sends a reservation-confirmed email to the guest.
     *
     * @param request notification payload
     */
    @PostMapping("/internal/notifications/reservation-confirmed")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "reservationConfirmedFallback")
    void sendReservationConfirmed(@RequestBody NotificationReservationRequest request);

    /**
     * Sends a check-in welcome email to the guest.
     *
     * @param request notification payload
     */
    @PostMapping("/internal/notifications/checkin")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "checkinFallback")
    void sendCheckin(@RequestBody NotificationCheckinRequest request);

    /**
     * Sends a checkout summary email with invoice lines to the guest.
     *
     * @param request notification payload
     */
    @PostMapping("/internal/notifications/checkout")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "checkoutFallback")
    void sendCheckout(@RequestBody NotificationCheckoutRequest request);

    /**
     * Fallback for sendReservationConfirmed — circuit open or call failed.
     *
     * @param request   original request
     * @param throwable cause
     */
    default void reservationConfirmedFallback(
            final NotificationReservationRequest request, final Throwable throwable) {
        LOG.warn("notification-service CB open — reservation-confirmed email suppressed [reservationId={}]: {}",
                request.reservationId(), throwable.getMessage());
    }

    /**
     * Fallback for sendCheckin — circuit open or call failed.
     *
     * @param request   original request
     * @param throwable cause
     */
    default void checkinFallback(
            final NotificationCheckinRequest request, final Throwable throwable) {
        LOG.warn("notification-service CB open — check-in email suppressed: {}", throwable.getMessage());
    }

    /**
     * Fallback for sendCheckout — circuit open or call failed.
     *
     * @param request   original request
     * @param throwable cause
     */
    default void checkoutFallback(
            final NotificationCheckoutRequest request, final Throwable throwable) {
        LOG.warn("notification-service CB open — checkout email suppressed: {}", throwable.getMessage());
    }
}
