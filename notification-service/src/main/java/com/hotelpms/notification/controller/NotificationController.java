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
     * @param request the notification payload
     * @return 204 No Content on success
     */
    @PostMapping("/reservation-confirmed")
    public ResponseEntity<Void> reservationConfirmed(@Valid @RequestBody final ReservationConfirmedRequest request) {
        log.debug("[NOTIFY] reservation-confirmed request received");
        notificationService.sendReservationConfirmed(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sends a check-in welcome email to the guest.
     *
     * @param request the notification payload
     * @return 204 No Content on success
     */
    @PostMapping("/checkin")
    public ResponseEntity<Void> checkin(@Valid @RequestBody final CheckinNotificationRequest request) {
        log.debug("[NOTIFY] checkin request received");
        notificationService.sendCheckin(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sends a check-out summary email including invoice detail to the guest.
     *
     * @param request the notification payload
     * @return 204 No Content on success
     */
    @PostMapping("/checkout")
    public ResponseEntity<Void> checkout(@Valid @RequestBody final CheckoutNotificationRequest request) {
        log.debug("[NOTIFY] checkout request received");
        notificationService.sendCheckout(request);
        return ResponseEntity.noContent().build();
    }
}
