package com.hotelpms.notification.controller;

import com.hotelpms.notification.dto.CheckinNotificationRequest;
import com.hotelpms.notification.dto.CheckoutNotificationRequest;
import com.hotelpms.notification.dto.ReservationConfirmedRequest;
import com.hotelpms.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal REST endpoints for dispatching transactional email notifications.
 *
 * <p>All endpoints require a valid {@code X-Internal-Signature} HMAC header
 * (validated by {@code InternalAuthFilter}) — they are not accessible from outside
 * the service mesh.
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
@Validated
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Sends a reservation-confirmed email to the guest.
     *
     * <p>Returns {@code 200 OK} with a {@code true} body rather than
     * {@code 204 No Content}: the caller's {@code NotificationClient} declares
     * a primitive {@code boolean} return type so Resilience4j's circuit-breaker
     * proxy can report success/failure to the caller — decoding an empty
     * no-content body into a primitive throws inside the proxy and is
     * misread as a failure, marking every successful send as failed.
     *
     * @param request the notification payload
     * @return 200 OK with body {@code true} on success
     */
    @PostMapping("/reservation-confirmed")
    public ResponseEntity<Boolean> reservationConfirmed(@Valid @RequestBody final ReservationConfirmedRequest request) {
        log.debug("[NOTIFY] reservation-confirmed request received");
        notificationService.sendReservationConfirmed(request);
        return ResponseEntity.ok(Boolean.TRUE);
    }

    /**
     * Sends a check-in welcome email to the guest.
     *
     * @param request the notification payload
     * @return 200 OK with body {@code true} on success
     */
    @PostMapping("/checkin")
    public ResponseEntity<Boolean> checkin(@Valid @RequestBody final CheckinNotificationRequest request) {
        log.debug("[NOTIFY] checkin request received");
        notificationService.sendCheckin(request);
        return ResponseEntity.ok(Boolean.TRUE);
    }

    /**
     * Sends a check-out summary email including invoice detail to the guest.
     *
     * @param request the notification payload
     * @return 200 OK with body {@code true} on success
     */
    @PostMapping("/checkout")
    public ResponseEntity<Boolean> checkout(@Valid @RequestBody final CheckoutNotificationRequest request) {
        log.debug("[NOTIFY] checkout request received");
        notificationService.sendCheckout(request);
        return ResponseEntity.ok(Boolean.TRUE);
    }
}
