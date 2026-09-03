package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.ChargeRequest;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceCreatedResponse;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.client.dto.StayInvoiceRequest;
import com.hotelpms.frontdesk.exception.ExternalServiceException;
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
import java.math.RoundingMode;
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
    private static final String ROOM_DESCRIPTION_PREFIX = "Room ";
    private static final String NIGHTS_DESCRIPTION_SUFFIX = " night(s)";

    private final BillingClient billingClient;
    private final RoomService roomService;
    private final StayRepository stayRepository;
    private final ReservationService reservationService;
    private final RatePricingService ratePricingService;
    private final CityTaxAssessmentService cityTaxAssessmentService;
    private final StayInvoiceResolver stayInvoiceResolver;

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
        return stayInvoiceResolver.resolve(stay);
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
            stay.setRoomChargeUnitPrice(roomCharge.snapshotUnitPrice());
            stay.setRoomChargeNights((int) roomCharge.nights());
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
     * Posts a supplementary {@code ROOM_NIGHT} charge for a stay extension (Parte 3) —
     * the added nights only, priced live via {@link RatePricingService} since there is no
     * reservation-line-item snapshot for nights beyond what was originally booked. The
     * original check-in {@code ROOM_NIGHT} charge is never touched — extending a stay
     * appends a charge, exactly like the tourist-tax rectification it runs alongside.
     *
     * <p>Unlike the check-in saga, a failure here is not swallowed and recorded for later
     * retry: an extension is an interactive operator action expecting a definite outcome,
     * so a billing failure fails the whole extension request.
     *
     * @param stay            the stay being extended
     * @param fromDate        the first added night (inclusive) — the stay's old check-out
     * @param toDateExclusive the new check-out (exclusive)
     * @return the posted charge id
     */
    UUID postExtensionRoomCharge(final Stay stay, final LocalDate fromDate, final LocalDate toDateExclusive) {
        final RoomResponse room = roomService.getRoomById(stay.getRoomId(), stay.getHotelId());
        final List<NightlyRate> nightlyRates = ratePricingService.resolveStayRates(
                room.roomType().id(), stay.getHotelId(), fromDate, toDateExclusive);
        final BigDecimal amount = nightlyRates.stream().map(NightlyRate::nightlyPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal unitPrice = uniformRate(nightlyRates);
        final long nights = ChronoUnit.DAYS.between(fromDate, toDateExclusive);
        final String description = ROOM_DESCRIPTION_PREFIX + stay.getRoomNumber()
                + " - extension - " + nights + NIGHTS_DESCRIPTION_SUFFIX;
        final ChargeRequest chargeRequest =
                new ChargeRequest(ROOM_NIGHT_CHARGE_TYPE, description, amount, stay.getId(), unitPrice, (int) nights);

        final ChargeResponse response;
        try {
            response = billingClient.addCharge(stay.getId(), chargeRequest);
        } catch (final FeignException ex) {
            throw new ExternalServiceException("EXTENSION_CHARGE_FAILED: " + ex.getMessage(), ex);
        }
        if (response == null || response.id() == null) {
            throw new ExternalServiceException(BILLING_SERVICE_UNAVAILABLE_REASON);
        }
        return response.id();
    }

    /**
     * Re-bills the room-night charge for a room change (Parte 6) whose new room has a
     * different {@code RoomType}: voids the single check-in {@code ROOM_NIGHT} charge
     * and reposts it as two segments, exactly like the ledger a hotel would actually
     * keep — never a live re-quote of nights already lived, per the same "an amount
     * already charged never silently changes" principle {@code CityTaxAssessment}
     * already applies.
     *
     * <ol>
     *   <li>consumed nights, {@code [actualCheckInTime, moveDate)}, at the OLD room's
     *       already-fixed {@code stay.getRoomChargeUnitPrice()} — never recomputed live,
     *       so a rate change since check-in can never alter what was already charged;
     *       skipped if zero or negative (room changed the same day as check-in);</li>
     *   <li>remaining nights, {@code [moveDate, expectedCheckOutDate)}, at the NEW
     *       room's live rate via {@link RatePricingService} — same resolution
     *       {@link #postExtensionRoomCharge} uses for genuinely new nights.</li>
     * </ol>
     *
     * <p>Like {@link #postExtensionRoomCharge}, a billing failure here is not
     * swallowed: a room change is an interactive operator action expecting a
     * definite outcome.
     *
     * @param stay      the stay being moved (still holding its OLD roomId/roomNumber —
     *                  called before those are updated)
     * @param newRoom   the destination room
     * @param moveDate  today — the boundary between the consumed and remaining segments
     * @return the new room's segment charge id and its snapshot, to persist onto
     *         {@code Stay.roomChargeId}/{@code roomChargeUnitPrice}/{@code roomChargeNights}
     */
    RoomChangeBillingResult postRoomChangeCharges(final Stay stay, final RoomResponse newRoom, final LocalDate moveDate) {
        try {
            billingClient.removeCharge(stay.getId(), stay.getRoomChargeId());
        } catch (final FeignException ex) {
            throw new ExternalServiceException("ROOM_CHANGE_VOID_FAILED: " + ex.getMessage(), ex);
        }

        final LocalDate consumedFrom = stay.getActualCheckInTime().toLocalDate();
        final long consumedNights = ChronoUnit.DAYS.between(consumedFrom, moveDate);
        if (consumedNights > 0) {
            final BigDecimal consumedAmount = stay.getRoomChargeUnitPrice().multiply(BigDecimal.valueOf(consumedNights));
            final String consumedDescription =
                    ROOM_DESCRIPTION_PREFIX + stay.getRoomNumber() + " - " + consumedNights + NIGHTS_DESCRIPTION_SUFFIX;
            postRoomNightCharge(stay, consumedDescription, consumedAmount, stay.getRoomChargeUnitPrice(), consumedNights);
        }

        final long remainingNights = ChronoUnit.DAYS.between(moveDate, stay.getExpectedCheckOutDate());
        final List<NightlyRate> newRoomRates = ratePricingService.resolveStayRates(
                newRoom.roomType().id(), stay.getHotelId(), moveDate, stay.getExpectedCheckOutDate());
        final BigDecimal remainingAmount =
                newRoomRates.stream().map(NightlyRate::nightlyPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal remainingUnitPrice = uniformRate(newRoomRates);
        final BigDecimal snapshotUnitPrice = remainingUnitPrice != null
                ? remainingUnitPrice
                : remainingAmount.divide(BigDecimal.valueOf(remainingNights), 2, RoundingMode.HALF_UP);
        final String remainingDescription =
                ROOM_DESCRIPTION_PREFIX + newRoom.roomNumber() + " - " + remainingNights + NIGHTS_DESCRIPTION_SUFFIX;
        final UUID newChargeId = postRoomNightCharge(
                stay, remainingDescription, remainingAmount, remainingUnitPrice, remainingNights);

        return new RoomChangeBillingResult(newChargeId, snapshotUnitPrice, (int) remainingNights);
    }

    private UUID postRoomNightCharge(
            final Stay stay, final String description, final BigDecimal amount,
            final BigDecimal displayUnitPrice, final long nights) {
        final ChargeRequest chargeRequest = new ChargeRequest(
                ROOM_NIGHT_CHARGE_TYPE, description, amount, stay.getId(), displayUnitPrice, (int) nights);
        final ChargeResponse response;
        try {
            response = billingClient.addCharge(stay.getId(), chargeRequest);
        } catch (final FeignException ex) {
            throw new ExternalServiceException("ROOM_CHANGE_CHARGE_FAILED: " + ex.getMessage(), ex);
        }
        if (response == null || response.id() == null) {
            throw new ExternalServiceException(BILLING_SERVICE_UNAVAILABLE_REASON);
        }
        return response.id();
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

        final String description =
                ROOM_DESCRIPTION_PREFIX + stay.getRoomNumber() + " - " + nights + NIGHTS_DESCRIPTION_SUFFIX;
        final ChargeRequest chargeRequest =
                new ChargeRequest(ROOM_NIGHT_CHARGE_TYPE, description, amount, stay.getId(), unitPrice, (int) nights);
        // unitPrice above is left null for a reservation-based stay (ChargeRequest's own
        // display-only field, see its javadoc) — the snapshot a future room change needs
        // (Parte 6) must never be null, so derive it from the total when it wasn't already
        // resolved per-night.
        final BigDecimal snapshotUnitPrice = unitPrice != null
                ? unitPrice
                : amount.divide(BigDecimal.valueOf(nights), 2, RoundingMode.HALF_UP);
        return new RoomChargeCalculation(chargeRequest, nights, snapshotUnitPrice);
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
     * @param chargeRequest      the {@code ROOM_NIGHT} charge request
     * @param nights             the resolved night count
     * @param snapshotUnitPrice  the per-night price to persist on {@code
     *                           Stay.roomChargeUnitPrice} — never {@code null},
     *                           unlike {@code chargeRequest.unitPrice()} which
     *                           is display-only and may legitimately be
     *                           {@code null} for a reservation-based stay
     */
    private record RoomChargeCalculation(ChargeRequest chargeRequest, long nights, BigDecimal snapshotUnitPrice) {
    }

    /**
     * The new room's re-billed segment, for {@code StayServiceImpl.changeRoom} to
     * persist onto the {@code Stay}.
     *
     * @param newRoomChargeId        the posted charge id for the remaining-nights segment
     * @param newRoomChargeUnitPrice its per-night price snapshot
     * @param newRoomChargeNights    its night count
     */
    record RoomChangeBillingResult(UUID newRoomChargeId, BigDecimal newRoomChargeUnitPrice, int newRoomChargeNights) {
    }
}
