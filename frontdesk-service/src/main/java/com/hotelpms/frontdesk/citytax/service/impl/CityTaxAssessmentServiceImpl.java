package com.hotelpms.frontdesk.citytax.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import com.hotelpms.frontdesk.citytax.repository.CityTaxAssessmentRepository;
import com.hotelpms.frontdesk.citytax.repository.CityTaxRateRepository;
import com.hotelpms.frontdesk.citytax.repository.HotelCategoryHistoryRepository;
import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentResult;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link CityTaxAssessmentService}.
 */
@Service
@RequiredArgsConstructor
public class CityTaxAssessmentServiceImpl implements CityTaxAssessmentService {

    private static final String ASSESSMENT_NOT_FOUND_MSG = "CITY_TAX_ASSESSMENT_NOT_FOUND";

    private final CityTaxAssessmentRepository cityTaxAssessmentRepository;
    private final CityTaxRateRepository cityTaxRateRepository;
    private final HotelCategoryHistoryRepository hotelCategoryHistoryRepository;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final CityTaxCalculator cityTaxCalculator;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Optional<CityTaxAssessment> assessFor(final Stay stay, final long nights) {
        Objects.requireNonNull(stay, "Stay cannot be null");

        final Optional<CityTaxAssessment> existing =
                cityTaxAssessmentRepository.findByStayIdAndHotelId(stay.getId(), stay.getHotelId());
        if (existing.isPresent()) {
            return existing; // Never recomputed once assessed — fiscal audit requirement.
        }

        final LocalDate firstNight = stay.getActualCheckInTime().toLocalDate();
        final String comuneCodice = resolveComuneCodice(stay.getHotelId());
        if (comuneCodice == null) {
            return Optional.empty(); // Hotel hasn't configured its comune yet — not a failure.
        }
        final String category = hotelCategoryHistoryRepository
                .findApplicableByHotelId(stay.getHotelId(), firstNight)
                .map(HotelCategoryHistory::getCategory)
                .orElse(null);
        if (category == null) {
            return Optional.empty(); // Hotel hasn't recorded a category for this date — not a failure.
        }
        final Optional<CityTaxRate> rate = cityTaxRateRepository
                .findApplicableByHotelId(stay.getHotelId(), comuneCodice, category, firstNight);
        if (rate.isEmpty()) {
            return Optional.empty(); // No rule configured for this (comune, category, date) — not a failure.
        }

        final CityTaxAssessmentResult result =
                cityTaxCalculator.assess(rate.get(), firstNight, nights, stay.getGuests());

        final CityTaxAssessment assessment = CityTaxAssessment.builder()
                .hotelId(stay.getHotelId())
                .stayId(stay.getId())
                .cityTaxRateId(rate.get().getId())
                .amountPerNightSnapshot(rate.get().getAmountPerNight())
                .maxTaxableNightsSnapshot(rate.get().getMaxTaxableNights())
                .exemptUnderAgeSnapshot(rate.get().getExemptUnderAge())
                .taxableGuests(result.taxableGuests())
                .taxableNights(result.taxableNights())
                .totalAmount(result.totalAmount())
                .assessedAt(LocalDateTime.now())
                .build();

        return Optional.of(cityTaxAssessmentRepository.save(assessment));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void markCharged(final UUID assessmentId, final UUID billingChargeId) {
        final CityTaxAssessment assessment = cityTaxAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException(ASSESSMENT_NOT_FOUND_MSG));
        assessment.setBillingChargeId(billingChargeId);
        cityTaxAssessmentRepository.save(assessment);
    }

    private String resolveComuneCodice(final UUID hotelId) {
        return hotelSettingsRepository.findById(hotelId)
                .map(HotelSettings::getComuneCodice)
                .orElse(null);
    }
}
