package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.ChargeRequest;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.client.dto.StayInvoiceRequest;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Owns the billing side-effects of check-in/check-out: opening the folio, posting
 * the room-night charge, and resolving the invoice to verify at check-out. Failures
 * here never block the check-in saga itself (backup/DECISIONS.md §2.2) — they're
 * recorded on the {@link Stay} for staff visibility/retry via
 * {@link StayServiceImpl#retryInvoiceCreation}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class StayBillingCoordinator {

    private static final String BILLING_SERVICE_UNAVAILABLE_REASON = "BILLING_SERVICE_UNAVAILABLE";
    private static final String ROOM_NIGHT_CHARGE_TYPE = "ROOM_NIGHT";

    private final BillingClient billingClient;
    private final RoomService roomService;
    private final StayRepository stayRepository;

    /**
     * Resolves the invoice to verify at check-out. Reservation-based stays are looked
     * up by reservationId (existing, unchanged path); walk-in stays (reservationId is
     * always {@code null} by definition) are looked up by the invoiceId stored on the
     * Stay at check-in time. A walk-in whose invoice was never created (billing-service
     * was unavailable at check-in) has no invoiceId to look up — returns {@code null},
     * which the caller already treats as BILLING_NOT_PAID.
     *
     * @param stay the stay being checked out
     * @return the invoice status response, or {@code null} if it cannot be resolved
     */
    InvoiceStatusResponse resolveInvoiceForCheckOut(final Stay stay) {
        log.debug("Verifying billing folio for stay: {} | reservationId={} | invoiceId={}",
                stay.getId(), stay.getReservationId(), stay.getInvoiceId());
        if (stay.getReservationId() != null) {
            return billingClient.getLatestInvoiceByReservation(stay.getReservationId());
        }
        if (stay.getInvoiceId() != null) {
            return billingClient.getInvoiceById(stay.getInvoiceId());
        }
        return null;
    }

    /**
     * Opens the billing folio for a just-checked-in stay and posts the room-night
     * charge. A stray retry (double-click, manual retry) must not re-add the room
     * charge and double-bill it.
     *
     * @param stay the just-checked-in stay
     */
    void openInvoiceForStay(final Stay stay) {
        if (stay.getInvoiceId() != null && !stay.isInvoiceCreationFailed()) {
            return;
        }

        UUID invoiceId = stay.getInvoiceId();
        if (invoiceId == null) {
            final StayInvoiceRequest invoiceReq = new StayInvoiceRequest(
                    stay.getId(), stay.getGuestId(), stay.getReservationId());
            final InvoiceCreatedResponse invoiceResp;
            try {
                invoiceResp = billingClient.createInvoiceForStay(invoiceReq);
            } catch (final FeignException ex) {
                // A 4xx here is a real billing-service rejection (e.g. a stale retry
                // racing INVOICE_ALREADY_EXISTS_FOR_STAY), not mere unavailability —
                // resilience4j.circuitbreaker.instances.billingService.ignoreExceptions
                // keeps it out of the fallback so it reaches this catch instead of being
                // silently absorbed (round 1 bug #1). Check-in itself still must not be
                // blocked by a billing problem (backup/DECISIONS.md §2.2), so it's
                // recorded for staff visibility/retry rather than rolling back the Saga.
                markInvoiceFlowFailed(stay, StayFailureReason.truncate(ex.getMessage()));
                return;
            }
            if (invoiceResp == null || invoiceResp.id() == null) {
                markInvoiceFlowFailed(stay, BILLING_SERVICE_UNAVAILABLE_REASON);
                return;
            }
            invoiceId = invoiceResp.id();
            stay.setInvoiceId(invoiceId);
        }

        final ChargeRequest chargeReq;
        try {
            chargeReq = buildRoomChargeRequest(stay);
        } catch (final NotFoundException ex) {
            log.error("[STAY] INVOICE_CREATION_FAILED | stayId={} | reason=ROOM_NOT_FOUND | detail={}",
                    stay.getId(), ex.getMessage());
            markInvoiceFlowFailed(stay, "ROOM_NOT_FOUND");
            return;
        }

        final ChargeResponse chargeResp;
        try {
            chargeResp = billingClient.addCharge(stay.getId(), chargeReq);
        } catch (final FeignException ex) {
            markInvoiceFlowFailed(stay, StayFailureReason.truncate(ex.getMessage()));
            return;
        }
        if (chargeResp == null || chargeResp.id() == null) {
            markInvoiceFlowFailed(stay, BILLING_SERVICE_UNAVAILABLE_REASON);
            return;
        }

        stay.setInvoiceCreationFailed(false);
        stay.setInvoiceCreationFailureReason(null);
        stayRepository.save(stay);
        log.info("[STAY] INVOICE_CREATED | stayId={} | invoiceId={} | roomChargeId={}",
                stay.getId(), invoiceId, chargeResp.id());
    }

    private void markInvoiceFlowFailed(final Stay stay, final String reason) {
        stay.setInvoiceCreationFailed(true);
        stay.setInvoiceCreationFailureReason(reason);
        stayRepository.save(stay);
        log.error("[STAY] INVOICE_CREATION_FAILED | stayId={} | reason={}", stay.getId(), reason);
    }

    private ChargeRequest buildRoomChargeRequest(final Stay stay) {
        final RoomResponse room = roomService.getRoomById(stay.getRoomId(), stay.getHotelId());
        final BigDecimal nightlyRate = room.roomType().basePrice();
        final long nights = Math.max(1, ChronoUnit.DAYS.between(
                stay.getActualCheckInTime().toLocalDate(), stay.getExpectedCheckOutDate()));
        final BigDecimal amount = nightlyRate.multiply(BigDecimal.valueOf(nights));
        final String description = "Room " + stay.getRoomNumber() + " - " + nights + " night(s)";
        return new ChargeRequest(ROOM_NIGHT_CHARGE_TYPE, description, amount, stay.getId());
    }
}
