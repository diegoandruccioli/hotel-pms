package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.config.BatchJobContext;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the check-out email once the check-out transaction has committed, on the
 * {@code checkoutEmailExecutor} thread pool. Generating the invoice PDF and talking
 * to the mail relay can take many seconds, and none of it may keep the operator (or
 * the stay row) waiting: the stay is already {@code CHECKED_OUT} when this runs.
 * A failure is recorded on the stay by {@link StayNotificationCoordinator}, which is
 * what shows the "retry" chip in the UI.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StayCheckoutEmailListener {

    private static final String JOB_USER = "checkout-email-job";

    private final StayRepository stayRepository;
    private final StayNotificationCoordinator notificationCoordinator;

    /**
     * Handles the committed check-out. Runs outside any HTTP request, so the outgoing
     * Feign calls are signed through {@link BatchJobContext} (same as the scheduled jobs).
     *
     * @param event the committed check-out
     */
    @Async("checkoutEmailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStayCheckedOut(final StayCheckedOutEvent event) {
        BatchJobContext.set(JOB_USER, event.hotelId().toString());
        try {
            stayRepository.findByIdAndHotelId(event.stayId(), event.hotelId())
                    .ifPresent(stay -> notificationCoordinator.sendCheckoutEmailIfPossible(stay, event.invoice()));
        } catch (final DataAccessException | FeignException ex) {
            log.error("[STAY] CHECKOUT_EMAIL_ASYNC_FAILED | stayId={} | reason={}", event.stayId(), ex.toString());
            markEmailFailed(event);
        } finally {
            BatchJobContext.clear();
        }
    }

    private void markEmailFailed(final StayCheckedOutEvent event) {
        try {
            stayRepository.findByIdAndHotelId(event.stayId(), event.hotelId()).ifPresent(stay -> {
                stay.setCheckoutEmailFailed(true);
                stay.setCheckoutEmailFailureReason(StayFailureReason.truncate("CHECKOUT_EMAIL_UNEXPECTED_ERROR"));
                stayRepository.save(stay);
            });
        } catch (final DataAccessException ex) {
            log.error("[STAY] CHECKOUT_EMAIL_FLAG_FAILED | stayId={} | reason={}", event.stayId(), ex.toString());
        }
    }
}
