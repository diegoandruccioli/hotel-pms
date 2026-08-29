package com.hotelpms.frontdesk.citytax.service;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.dto.CityTaxBackfillResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxConfigurationStatusResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxUnassessedSummaryResponse;
import com.hotelpms.frontdesk.stays.domain.Stay;

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
}
