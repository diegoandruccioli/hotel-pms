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
 * All methods have circuit-breaker fallbacks — notification failures must
 * never block the calling business transaction. Unlike a purely void
 * fire-and-forget call, each method returns {@code true} when the request
 * reached notification-service and {@code false} when the fallback fired
 * (circuit open or call failed), so callers can persist durable failure
 * state and offer a manual retry (mirrors {@code BillingClient.createInvoiceForStay},
 * which already signals failure via a {@code null} return).
 */
@FeignClient(name = "notification-service")
public interface NotificationClient {

    Logger LOG = LoggerFactory.getLogger(NotificationClient.class);

    /**
     * Sends a reservation-confirmed email to the guest.
     *
     * @param request notification payload
     * @return {@code true} if the request reached notification-service, {@code false} if suppressed
     */
    @PostMapping("/internal/notifications/reservation-confirmed")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "reservationConfirmedFallback")
    boolean sendReservationConfirmed(@RequestBody NotificationReservationRequest request);

    /**
     * Sends a check-in welcome email to the guest.
     *
     * @param request notification payload
     * @return {@code true} if the request reached notification-service, {@code false} if suppressed
     */
    @PostMapping("/internal/notifications/checkin")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "checkinFallback")
    boolean sendCheckin(@RequestBody NotificationCheckinRequest request);

    /**
     * Sends a checkout summary email with invoice lines to the guest.
     *
     * @param request notification payload
     * @return {@code true} if the request reached notification-service, {@code false} if suppressed
     */
    @PostMapping("/internal/notifications/checkout")
    @CircuitBreaker(name = "notificationService", fallbackMethod = "checkoutFallback")
    boolean sendCheckout(@RequestBody NotificationCheckoutRequest request);

    /**
     * Fallback for sendReservationConfirmed — circuit open or call failed.
     *
     * @param request   original request
     * @param throwable cause
     * @return {@code false}
     */
    default boolean reservationConfirmedFallback(
            final NotificationReservationRequest request, final Throwable throwable) {
        LOG.warn("notification-service CB open — reservation-confirmed email suppressed [reservationId={}]: {}",
                request.reservationId(), throwable.getMessage());
        return false;
    }

    /**
     * Fallback for sendCheckin — circuit open or call failed.
     *
     * @param request   original request
     * @param throwable cause
     * @return {@code false}
     */
    default boolean checkinFallback(
            final NotificationCheckinRequest request, final Throwable throwable) {
        LOG.warn("notification-service CB open — check-in email suppressed: {}", throwable.getMessage());
        return false;
    }

    /**
     * Fallback for sendCheckout — circuit open or call failed.
     *
     * @param request   original request
     * @param throwable cause
     * @return {@code false}
     */
    default boolean checkoutFallback(
            final NotificationCheckoutRequest request, final Throwable throwable) {
        LOG.warn("notification-service CB open — checkout email suppressed: {}", throwable.getMessage());
        return false;
    }
}
