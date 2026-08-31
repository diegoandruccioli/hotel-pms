package com.hotelpms.frontdesk.citytax.service;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.dto.CityTaxBackfillResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxConfigurationStatusResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxUnassessedSummaryResponse;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the applicable tourist-tax rule for a stay and records the
 * resulting assessment. {@link #assessFor} is consumed internally by
 * {@code StayBillingCoordinator} at check-in; the rest of this interface
 * backs the check-in pre-flight warning, the Dashboard alert banner, and the
 * admin backfill flow (all exposed on {@code StayController} under {@code
 * /api/v1/stays/city-tax/**} — the rules/history admin surface itself is
 * {@code CityTaxRateAdminService}/{@code HotelCategoryHistoryService}).
 */
public interface CityTaxAssessmentService {

    /**
     * Assesses and persists the tourist tax for a stay. A row is <em>always</em>
     * written — when the hotel's comune/category/rate isn't configured for the
     * stay's check-in date, or the hotel has declared the tax not applicable,
     * the row still records {@code totalAmount = 0} with the reason on {@code
     * unassessedReason}, rather than being silently skipped: "which stays were
     * never actually assessed, and why" must be a queryable fact, not
     * indistinguishable from "assessed and zero because every guest was
     * exempt". The {@code Optional} return is therefore always present in
     * practice; it stays {@code Optional} only so a mocked collaborator that
     * never stubs this call keeps defaulting to empty in existing tests.
     *
     * <p>Idempotent: if an assessment already exists for this stay ({@code
     * Stay.id}), the existing one is returned unchanged rather than
     * recomputed (fiscal audit requirement — an assessed amount, including a
     * deliberately zero one, is never silently recalculated).
     *
     * @param stay   the stay to assess (its {@code guests} must be loaded)
     * @param nights the number of nights to assess, already computed by the
     *               caller for the {@code ROOM_NIGHT} charge — never
     *               recalculated here, so the two charges can never disagree
     *               on night count
     * @return the assessment (always present)
     */
    Optional<CityTaxAssessment> assessFor(Stay stay, long nights);

    /**
     * Read-only lookup of a stay's assessment, if one was ever recorded — unlike {@link
     * #assessFor}, never assesses or persists anything. Used to attach {@code
     * StayResponse.cityTaxWarning} to the check-in response without risking a second,
     * wrongly-parameterized assessment call.
     *
     * @param stayId  the stay UUID
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @return the assessment, if one exists
     */
    Optional<CityTaxAssessment> findAssessment(UUID stayId, UUID hotelId);

    /**
     * Records the billing-service charge id on an existing assessment, once
     * the {@code CITY_TAX} charge has been successfully posted — the
     * per-charge idempotency guard for the check-in retry path.
     *
     * @param assessmentId the assessment UUID
     * @param billingChargeId the charge id returned by billing-service
     */
    void markCharged(UUID assessmentId, UUID billingChargeId);

    /**
     * Reports whether a check-in for this hotel <em>today</em> would actually
     * get its tourist tax assessed — the pre-flight check the check-in form
     * calls before submitting, so a missing configuration surfaces before the
     * stay is created rather than only after.
     *
     * @param hotelId the hotel UUID
     * @return the configuration status
     */
    CityTaxConfigurationStatusResponse checkConfigurationStatus(UUID hotelId);

    /**
     * Summarizes stays whose tourist tax was never assessed because of a
     * configuration gap ({@code NOT_APPLICABLE} excluded — a deliberate
     * declaration, never a gap). Drives the Dashboard alert banner.
     *
     * @param hotelId the hotel UUID
     * @return the summary
     */
    CityTaxUnassessedSummaryResponse getUnassessedSummary(UUID hotelId);

    /**
     * Dry-run: computes what a backfill would charge, against each affected
     * stay's own check-in date (never today's rate), without posting anything
     * or writing any assessment.
     *
     * @param hotelId the hotel UUID
     * @return the preview
     */
    CityTaxBackfillResponse previewBackfill(UUID hotelId);

    /**
     * Posts the {@code CITY_TAX} charge for every affected stay that still has
     * an open invoice, and corrects its assessment row. Stays whose invoice is
     * no longer open are left untouched — never re-opens or amends a fiscal
     * document already closed.
     *
     * @param hotelId the hotel UUID
     * @return the outcome, same shape as the preview but with lines actually charged
     */
    CityTaxBackfillResponse confirmBackfill(UUID hotelId);

    /**
     * Parte 1/Parte 6: a guest added mid-stay generates a supplementary line, never a
     * recomputation of the original assessment — {@code city_tax_assessments.total_amount}
     * is increased by the new guest's own contribution and a matching {@code
     * CITY_TAX} charge is posted if the invoice is still open; every other snapshot field
     * on the original assessment is left exactly as first recorded.
     *
     * <p>A no-op when the stay has no assessment yet, or its assessment records an {@code
     * unassessedReason} (nothing to add a rectification onto — a later backfill run
     * covers the whole stay, this new guest included), or the added guest's own remaining
     * nights aren't covered by any configured rate (the same gap the stay itself would hit).
     *
     * @param stay     the stay the guest was added to (its {@code hotelId} and {@code
     *                 expectedCheckOutDate} are used to resolve the guest's remaining nights)
     * @param newGuest the guest just added, with {@code arrivalDate} already set
     */
    void rectifyForGuestAdded(Stay stay, StayGuest newGuest);

    /**
     * Parte 3/Parte 6: a stay extended past its original check-out generates a
     * supplementary line for every current guest, for {@code [fromDate, toDateExclusive)}
     * — same rectification mechanics as {@link #rectifyForGuestAdded}, sharing its
     * "append, never rewrite the original assessment" contract.
     *
     * @param stay          the stay being extended (its {@code hotelId} and current
     *                      guests are used to resolve and tax the added nights)
     * @param fromDate      the first added night (inclusive) — the stay's old check-out
     * @param toDateExclusive the new check-out (exclusive)
     */
    void rectifyForStayExtended(Stay stay, LocalDate fromDate, LocalDate toDateExclusive);

    /**
     * Reverses the rectification {@link #rectifyForGuestAdded} made for a guest who has
     * since been removed from the stay: subtracts exactly that guest's own contribution
     * from the assessment's running total, and voids the specific billing-service charge
     * it posted (via {@code StayGuest.cityTaxChargeId}) if the invoice is still open.
     *
     * <p>A no-op when the removed guest never had a charge of their own posted —
     * {@code cityTaxChargeId} is {@code null} for a guest present at check-in (their
     * tax is part of the stay's original, immutable assessment, never attributable to
     * one guest) or when the original rectification charge never actually posted. If
     * the invoice has since closed, the assessment total is still corrected but the
     * already-issued charge is left standing — never re-opens or amends a closed
     * fiscal document, same rule as everywhere else in this service.
     *
     * @param stay         the stay the guest was removed from
     * @param removedGuest the guest just removed, with its lifecycle fields still set
     */
    void rectifyForGuestRemoved(Stay stay, StayGuest removedGuest);
}
