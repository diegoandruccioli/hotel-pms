package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.ChargeRequest;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.client.dto.StayInvoiceRequest;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.reservations.dto.ReservedRoomCharge;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
    private static final String CITY_TAX_CHARGE_TYPE = "CITY_TAX";

    private final BillingClient billingClient;
    private final RoomService roomService;
    private final StayRepository stayRepository;
    private final ReservationService reservationService;
    private final RatePricingService ratePricingService;
    private final CityTaxAssessmentService cityTaxAssessmentService;

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
     * and (if applicable) tourist-tax charges. A stray retry (double-click, manual
     * retry) must not re-add a charge that was already posted successfully and
     * double-bill it — {@link Stay#getRoomChargeId()} and {@code
     * CityTaxAssessment.billingChargeId} are independent per-charge idempotency
     * guards, so a failure on one charge never causes a retry to re-post the other.
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

        final RoomChargeCalculation roomCharge;
        try {
            roomCharge = buildRoomChargeCalculation(stay);
        } catch (final NotFoundException ex) {
            log.error("[STAY] INVOICE_CREATION_FAILED | stayId={} | reason=ROOM_NOT_FOUND | detail={}",
                    stay.getId(), ex.getMessage());
            markInvoiceFlowFailed(stay, "ROOM_NOT_FOUND");
            return;
        }

        if (stay.getRoomChargeId() == null) {
            final ChargeResponse chargeResp;
            try {
                chargeResp = billingClient.addCharge(stay.getId(), roomCharge.chargeRequest());
            } catch (final FeignException ex) {
                markInvoiceFlowFailed(stay, StayFailureReason.truncate(ex.getMessage()));
                return;
            }
            if (chargeResp == null || chargeResp.id() == null) {
                markInvoiceFlowFailed(stay, BILLING_SERVICE_UNAVAILABLE_REASON);
                return;
            }
            stay.setRoomChargeId(chargeResp.id());
        }

        if (!postCityTaxChargeIfNeeded(stay, roomCharge.nights())) {
            return;
        }

        stay.setInvoiceCreationFailed(false);
        stay.setInvoiceCreationFailureReason(null);
        stayRepository.save(stay);
        log.info("[STAY] INVOICE_CREATED | stayId={} | invoiceId={} | roomChargeId={}",
                stay.getId(), invoiceId, stay.getRoomChargeId());
    }

    /**
     * Assesses and, if applicable, posts the {@code CITY_TAX} charge — a no-op
     * (returning {@code true}) when the hotel's comune/category/rate isn't
     * configured, when the assessment was already charged on a prior attempt,
     * or when the assessed total is zero (all guests exempt): no zero-amount
     * line is ever posted, but the assessment itself is still recorded by
     * {@link CityTaxAssessmentService#assessFor} for the audit trail.
     *
     * @param stay   the just-checked-in stay
     * @param nights the night count already computed for the room charge —
     *               reused here so the two lines can never disagree
     * @return {@code false} if posting failed and the caller must stop (the
     *         failure has already been recorded on the stay); {@code true} otherwise
     */
    private boolean postCityTaxChargeIfNeeded(final Stay stay, final long nights) {
        final Optional<CityTaxAssessment> assessment = cityTaxAssessmentService.assessFor(stay, nights);
        if (assessment.isEmpty()) {
            return true;
        }
        final CityTaxAssessment cityTax = assessment.get();
        if (cityTax.getBillingChargeId() != null || cityTax.getTotalAmount().signum() <= 0) {
            return true;
        }

        final ChargeResponse cityTaxResp;
        try {
            cityTaxResp = billingClient.addCharge(stay.getId(), buildCityTaxChargeRequest(stay, cityTax));
        } catch (final FeignException ex) {
            markInvoiceFlowFailed(stay, StayFailureReason.truncate(ex.getMessage()));
            return false;
        }
        if (cityTaxResp == null || cityTaxResp.id() == null) {
            markInvoiceFlowFailed(stay, BILLING_SERVICE_UNAVAILABLE_REASON);
            return false;
        }
        cityTaxAssessmentService.markCharged(cityTax.getId(), cityTaxResp.id());
        return true;
    }

    /**
     * Builds the tourist-tax charge for an assessed stay.
     *
     * @param stay      the just-checked-in stay
     * @param assessment the recorded assessment (already snapshotted, never recomputed here)
     * @return the charge request to post to billing-service
     */
    private ChargeRequest buildCityTaxChargeRequest(final Stay stay, final CityTaxAssessment assessment) {
        final String description = "Imposta di soggiorno - " + assessment.getTaxableNights()
                + " night(s) x " + assessment.getTaxableGuests() + " guest(s)";
        return new ChargeRequest(CITY_TAX_CHARGE_TYPE, description, assessment.getTotalAmount(), stay.getId(),
                assessment.getAmountPerNightSnapshot(), assessment.getTaxableNights());
    }

    private void markInvoiceFlowFailed(final Stay stay, final String reason) {
        stay.setInvoiceCreationFailed(true);
        stay.setInvoiceCreationFailureReason(reason);
        stayRepository.save(stay);
        log.error("[STAY] INVOICE_CREATION_FAILED | stayId={} | reason={}", stay.getId(), reason);
    }

    /**
     * Builds the room-night charge for a just-checked-in stay.
     *
     * <p>Reservation-based stays bill exactly the price snapshotted on the
     * reservation's line item at booking time (via {@link ReservationService
     * #getReservedRoomCharge}) — read once, never recomputed, which is the
     * reconciliation fix: what the guest was quoted at booking is what they're
     * billed at check-in, even if {@code RoomType.basePrice} or the {@code
     * rate_seasons} configuration changed in between. {@code nights} for that
     * charge comes from the same reservation snapshot (the reservation's own
     * {@code checkInDate}/{@code checkOutDate}) rather than from {@code
     * stay.getActualCheckInTime()} — a late or early arrival must not make the
     * night count on the invoice disagree with the amount actually billed.
     *
     * <p>Walk-ins (no reservation to snapshot from), and the defensive fallback
     * for a reservation-based stay whose snapshot is somehow missing, resolve
     * live via {@link RatePricingService} instead of reading {@code
     * RoomType.basePrice} directly — so walk-ins get season-aware pricing too;
     * for that path {@code nights} is necessarily derived from the stay's own
     * actual/expected dates, since there is no reservation to read it from.
     *
     * @param stay the just-checked-in stay
     * @return the charge request to post to billing-service, paired with the
     *         resolved night count — reused as-is for the {@code CITY_TAX}
     *         assessment so the two charges can never disagree on nights
     */
    private RoomChargeCalculation buildRoomChargeCalculation(final Stay stay) {
        final RoomResponse room = roomService.getRoomById(stay.getRoomId(), stay.getHotelId());

        final Optional<ReservedRoomCharge> reservedCharge = stay.getReservationId() == null
                ? Optional.empty()
                : reservationService.getReservedRoomCharge(stay.getReservationId(), stay.getRoomId(), stay.getHotelId());

        final BigDecimal amount;
        final long nights;
        BigDecimal unitPrice = null;
        if (reservedCharge.isPresent()) {
            amount = reservedCharge.get().price();
            nights = reservedCharge.get().nights();
        } else {
            final LocalDate checkInDate = stay.getActualCheckInTime().toLocalDate();
            nights = Math.max(1, ChronoUnit.DAYS.between(checkInDate, stay.getExpectedCheckOutDate()));
            final LocalDate checkOutForPricing = checkInDate.plusDays(nights);
            final List<NightlyRate> nightlyRates = ratePricingService.resolveStayRates(
                    room.roomType().id(), stay.getHotelId(), checkInDate, checkOutForPricing);
            amount = nightlyRates.stream().map(NightlyRate::nightlyPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
            unitPrice = uniformRate(nightlyRates);
        }

        final String description = "Room " + stay.getRoomNumber() + " - " + nights + " night(s)";
        final ChargeRequest chargeRequest =
                new ChargeRequest(ROOM_NIGHT_CHARGE_TYPE, description, amount, stay.getId(), unitPrice, (int) nights);
        return new RoomChargeCalculation(chargeRequest, nights);
    }

    /**
     * Returns the common nightly price when every night of the stay resolved to
     * the same rate, or {@code null} when they differ (e.g. the stay crosses a
     * rate-season boundary) — {@code amount} above is always correct either way,
     * this is display/audit metadata only.
     *
     * @param nightlyRates the resolved rate for each night of the stay; never empty
     * @return the uniform nightly price, or {@code null} if rates vary by night
     */
    private static BigDecimal uniformRate(final List<NightlyRate> nightlyRates) {
        final BigDecimal first = nightlyRates.get(0).nightlyPrice();
        final boolean allEqual = nightlyRates.stream()
                .allMatch(rate -> rate.nightlyPrice().compareTo(first) == 0);
        return allEqual ? first : null;
    }

    /**
     * The room charge to post (or already posted), paired with the resolved
     * night count for reuse by the {@code CITY_TAX} assessment.
     *
     * @param chargeRequest the {@code ROOM_NIGHT} charge request
     * @param nights        the resolved night count
     */
    private record RoomChargeCalculation(ChargeRequest chargeRequest, long nights) {
    }
}
