package com.hotelpms.frontdesk.citytax.repository;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.domain.CityTaxUnassessedReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link CityTaxAssessment}.
 */
@Repository
public interface CityTaxAssessmentRepository extends JpaRepository<CityTaxAssessment, UUID> {

    /**
     * Finds the assessment for a stay, scoped to a hotel — a stay always has
     * at most one assessment ({@code uq_city_tax_assessments_stay}).
     *
     * @param stayId  the stay UUID
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @return the assessment, if one was ever recorded for this stay
     */
    Optional<CityTaxAssessment> findByStayIdAndHotelId(UUID stayId, UUID hotelId);

    /**
     * Lists every unassessed stay for a hotel whose reason is one of {@code reasons} —
     * used by the dashboard summary (all reasons) and by the backfill flow (only the
     * configuration-gap reasons, excluding {@code NOT_APPLICABLE} which is never a gap
     * to fix).
     *
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @param reasons the reasons to include
     * @return matching assessments, unordered
     */
    List<CityTaxAssessment> findByHotelIdAndUnassessedReasonIn(UUID hotelId, Collection<CityTaxUnassessedReason> reasons);
}
