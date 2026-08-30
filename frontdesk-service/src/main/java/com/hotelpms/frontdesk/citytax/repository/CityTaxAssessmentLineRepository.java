package com.hotelpms.frontdesk.citytax.repository;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessmentLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link CityTaxAssessmentLine}.
 */
@Repository
public interface CityTaxAssessmentLineRepository extends JpaRepository<CityTaxAssessmentLine, UUID> {

    /**
     * Lists every line for an assessment, in the order they were written
     * (equivalently, chronological — segments are always appended in date order).
     *
     * @param assessmentId the parent assessment UUID
     * @return the lines
     */
    List<CityTaxAssessmentLine> findByAssessmentIdOrderByFromDate(UUID assessmentId);
}
