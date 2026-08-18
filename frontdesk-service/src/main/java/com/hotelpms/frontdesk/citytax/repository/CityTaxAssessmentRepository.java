package com.hotelpms.frontdesk.citytax.repository;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
