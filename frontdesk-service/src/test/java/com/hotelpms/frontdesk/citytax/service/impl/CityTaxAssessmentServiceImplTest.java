package com.hotelpms.frontdesk.citytax.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import com.hotelpms.frontdesk.citytax.repository.CityTaxAssessmentRepository;
import com.hotelpms.frontdesk.citytax.repository.CityTaxRateRepository;
import com.hotelpms.frontdesk.citytax.repository.HotelCategoryHistoryRepository;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentResult;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityTaxAssessmentServiceImplTest {

    private static final String COMUNE_CODICE = "099014";
    private static final String CATEGORY = "4_STAR";
    private static final LocalDate FIRST_NIGHT = LocalDate.of(2026, 8, 10);
    private static final LocalDate RATE_VALID_FROM = LocalDate.of(2026, 1, 1);
    private static final int MAX_TAXABLE_NIGHTS = 7;
    private static final int EXEMPT_UNDER_AGE = 14;
    private static final long NIGHTS = 3L;

    @Mock
    private CityTaxAssessmentRepository cityTaxAssessmentRepository;

    @Mock
    private CityTaxRateRepository cityTaxRateRepository;

    @Mock
    private HotelCategoryHistoryRepository hotelCategoryHistoryRepository;

    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    @Mock
    private CityTaxCalculator cityTaxCalculator;

    @InjectMocks
    private CityTaxAssessmentServiceImpl cityTaxAssessmentService;

    private UUID hotelId;
    private UUID stayId;
    private Stay stay;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();
        stayId = UUID.randomUUID();
        stay = Stay.builder()
                .id(stayId)
                .hotelId(hotelId)
                .actualCheckInTime(FIRST_NIGHT.atStartOfDay())
                .build();
    }

    @Test
    void assessForAlreadyAssessedStayReturnsExistingWithoutRecomputing() {
        final CityTaxAssessment existing = CityTaxAssessment.builder().id(UUID.randomUUID()).build();
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(existing));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isPresent());
        assertEquals(existing, result.get());
        verify(cityTaxCalculator, never()).assess(any(), any(), anyLong(), any());
    }

    @Test
    void assessForHotelWithoutComuneConfiguredReturnsEmpty() {
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.empty());

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isEmpty());
        verify(cityTaxAssessmentRepository, never()).save(any());
    }

    @Test
    void assessForHotelWithoutCategoryRecordedReturnsEmpty() {
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId))
                .thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT)).thenReturn(Optional.empty());

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isEmpty());
        verify(cityTaxAssessmentRepository, never()).save(any());
    }

    @Test
    void assessForNoApplicableRateReturnsEmpty() {
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId))
                .thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findApplicableByHotelId(hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT))
                .thenReturn(Optional.empty());

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isEmpty());
        verify(cityTaxAssessmentRepository, never()).save(any());
    }

    @Test
    void assessForApplicableRateComputesAndPersistsSnapshot() {
        final CityTaxRate rate = CityTaxRate.builder()
                .id(UUID.randomUUID())
                .hotelId(hotelId)
                .comuneCodice(COMUNE_CODICE)
                .category(CATEGORY)
                .amountPerNight(new BigDecimal("2.50"))
                .maxTaxableNights(MAX_TAXABLE_NIGHTS)
                .exemptUnderAge(EXEMPT_UNDER_AGE)
                .validFrom(RATE_VALID_FROM)
                .build();

        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId))
                .thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findApplicableByHotelId(hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT))
                .thenReturn(Optional.of(rate));
        when(cityTaxCalculator.assess(rate, FIRST_NIGHT, NIGHTS, stay.getGuests()))
                .thenReturn(new CityTaxAssessmentResult(3, 2, new BigDecimal("15.00")));
        when(cityTaxAssessmentRepository.save(any(CityTaxAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isPresent());
        final CityTaxAssessment saved = result.get();
        assertEquals(hotelId, saved.getHotelId());
        assertEquals(stayId, saved.getStayId());
        assertEquals(rate.getId(), saved.getCityTaxRateId());
        assertEquals(new BigDecimal("2.50"), saved.getAmountPerNightSnapshot());
        assertEquals(MAX_TAXABLE_NIGHTS, saved.getMaxTaxableNightsSnapshot());
        assertEquals(EXEMPT_UNDER_AGE, saved.getExemptUnderAgeSnapshot());
        assertEquals(2, saved.getTaxableGuests());
        assertEquals(3, saved.getTaxableNights());
        assertEquals(new BigDecimal("15.00"), saved.getTotalAmount());
    }

    @Test
    void markChargedSetsBillingChargeId() {
        final UUID assessmentId = UUID.randomUUID();
        final UUID chargeId = UUID.randomUUID();
        final CityTaxAssessment assessment = CityTaxAssessment.builder().id(assessmentId).build();
        when(cityTaxAssessmentRepository.findById(assessmentId)).thenReturn(Optional.of(assessment));
        lenient().when(cityTaxAssessmentRepository.save(assessment)).thenReturn(assessment);

        cityTaxAssessmentService.markCharged(assessmentId, chargeId);

        assertEquals(chargeId, assessment.getBillingChargeId());
        verify(cityTaxAssessmentRepository, times(1)).save(assessment);
    }

    @Test
    void markChargedForUnknownAssessmentThrowsNotFound() {
        final UUID assessmentId = UUID.randomUUID();
        when(cityTaxAssessmentRepository.findById(assessmentId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> cityTaxAssessmentService.markCharged(assessmentId, UUID.randomUUID()));
    }

    private static HotelSettings settingsWithComune(final String comuneCodice) {
        final HotelSettings settings = new HotelSettings();
        settings.setComuneCodice(comuneCodice);
        return settings;
    }

    private static HotelCategoryHistory categoryHistory(final String category) {
        return HotelCategoryHistory.builder().id(UUID.randomUUID()).category(category).build();
    }
}
