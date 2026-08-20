package com.hotelpms.frontdesk.citytax.service;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.stays.domain.Stay;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the applicable tourist-tax rule for a stay and records the
 * resulting assessment. Consumed internally by
 * {@code StayBillingCoordinator} at check-in — not exposed as its own
 * top-level REST resource (the rules/history admin surface is
 * {@code CityTaxRateAdminService}/{@code HotelCategoryHistoryService}).
 */
public interface CityTaxAssessmentService {

    /**
     * Assesses and persists the tourist tax for a stay, or does nothing and
     * returns empty if the hotel's comune/category/rate isn't configured for
     * the stay's check-in date — a normal "not configured yet" case, not a
     * failure. Idempotent: if an assessment already exists for this stay
     * ({@code Stay.id}), the existing one is returned unchanged rather than
     * recomputed (fiscal audit requirement — an assessed amount is never
     * silently recalculated).
     *
     * @param stay   the stay to assess (its {@code guests} must be loaded)
     * @param nights the number of nights to assess, already computed by the
     *               caller for the {@code ROOM_NIGHT} charge — never
     *               recalculated here, so the two charges can never disagree
     *               on night count
     * @return the assessment, or empty if nothing is configured to assess against
     */
    Optional<CityTaxAssessment> assessFor(Stay stay, long nights);

    /**
     * Records the billing-service charge id on an existing assessment, once
     * the {@code CITY_TAX} charge has been successfully posted — the
     * per-charge idempotency guard for the check-in retry path.
     *
     * @param assessmentId the assessment UUID
     * @param billingChargeId the charge id returned by billing-service
     */
    void markCharged(UUID assessmentId, UUID billingChargeId);
}
