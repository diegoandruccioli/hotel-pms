package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.ChargeLineDto;
import com.hotelpms.frontdesk.client.dto.GuestResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceForEmailResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.client.dto.NotificationChargeLineDto;
import com.hotelpms.frontdesk.client.dto.NotificationCheckoutRequest;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sends the checkout-summary transactional email (via notification-service), gated
 * by per-hotel settings. Failure never blocks check-out itself — it's recorded on
 * the {@link Stay} for staff retry via {@link StayServiceImpl#retryCheckoutEmail}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class StayNotificationCoordinator {

    private static final String NOTIFICATION_SERVICE_UNAVAILABLE_REASON = "NOTIFICATION_SERVICE_UNAVAILABLE";
    private static final String DEFAULT_CURRENCY = "EUR";

    private final NotificationClient notificationClient;
    private final GuestClient guestClient;
    private final BillingClient billingClient;
    private final HotelSettingsService hotelSettingsService;
    private final StayRepository stayRepository;

    /**
     * Sends the checkout email if enabled in hotel settings; records success or
     * failure on the stay either way (a Feign/DB failure never blocks check-out,
     * which has already committed by the time this runs).
     *
     * @param stay    the just-checked-out stay
     * @param invoice the invoice verified as PAID during check-out
     */
    void sendCheckoutEmailIfPossible(final Stay stay, final InvoiceStatusResponse invoice) {
        try {
            final HotelSettingsResponse settings = hotelSettingsService.getOrCreate(stay.getHotelId());
            if (!settings.sendCheckoutEmail()) {
                return;
            }
            final GuestResponse guest = guestClient.getGuestById(stay.getGuestId());
            final List<NotificationChargeLineDto> lines;
            final String currency;
            if (invoice.id() != null) {
                final InvoiceForEmailResponse detail = billingClient.getInvoiceForEmail(invoice.id());
                lines = detail.charges() != null
                        ? detail.charges().stream()
                                .map((ChargeLineDto c) -> new NotificationChargeLineDto(c.description(), c.amount()))
                                .toList()
                        : List.of();
                currency = detail.currency() != null ? detail.currency() : DEFAULT_CURRENCY;
            } else {
                lines = List.of();
                currency = DEFAULT_CURRENCY;
            }
            final boolean sent = notificationClient.sendCheckout(new NotificationCheckoutRequest(
                    guest.email(),
                    guest.firstName() + " " + guest.lastName(),
                    settings.hotelName(),
                    stay.getRoomNumber(),
                    stay.getActualCheckInTime(),
                    stay.getActualCheckOutTime(),
                    lines,
                    invoice.totalAmount(),
                    currency,
                    "it",
                    settings.emailSubjectCheckout(),
                    settings.emailGreetingText(),
                    settings.logoUrl()));
            stay.setCheckoutEmailFailed(!sent);
            stay.setCheckoutEmailFailureReason(sent ? null : NOTIFICATION_SERVICE_UNAVAILABLE_REASON);
            stayRepository.save(stay);
        } catch (final FeignException | DataAccessException ex) {
            log.warn("[STAY] CHECKOUT_EMAIL_SKIPPED | stayId={} | reason={}", stay.getId(), ex.getMessage());
            stay.setCheckoutEmailFailed(true);
            stay.setCheckoutEmailFailureReason(StayFailureReason.truncate(ex.getMessage()));
            stayRepository.save(stay);
        }
    }
}
