package com.hotelpms.notification.service;

import com.hotelpms.notification.dto.CheckinNotificationRequest;
import com.hotelpms.notification.dto.CheckoutNotificationRequest;
import com.hotelpms.notification.dto.ReservationConfirmedRequest;

/**
 * Sends transactional email notifications to hotel guests.
 */
public interface NotificationService {

    /**
     * Sends a reservation-confirmed email to the guest.
     *
     * @param request the notification payload
     */
    void sendReservationConfirmed(ReservationConfirmedRequest request);

    /**
     * Sends a check-in welcome email to the guest.
     *
     * @param request the notification payload
     */
    void sendCheckin(CheckinNotificationRequest request);

    /**
     * Sends a check-out summary email including invoice detail to the guest.
     *
     * @param request the notification payload
     */
    void sendCheckout(CheckoutNotificationRequest request);
}
