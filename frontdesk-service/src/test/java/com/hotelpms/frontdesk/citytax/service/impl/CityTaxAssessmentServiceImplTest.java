package com.hotelpms.frontdesk.citytax.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessment;
import com.hotelpms.frontdesk.citytax.domain.CityTaxAssessmentLine;
import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.domain.CityTaxUnassessedReason;
import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import com.hotelpms.frontdesk.citytax.dto.CityTaxBackfillResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxConfigurationStatusResponse;
import com.hotelpms.frontdesk.citytax.dto.CityTaxUnassessedSummaryResponse;
import com.hotelpms.frontdesk.citytax.repository.CityTaxAssessmentLineRepository;
import com.hotelpms.frontdesk.citytax.repository.CityTaxAssessmentRepository;
import com.hotelpms.frontdesk.citytax.repository.CityTaxRateRepository;
import com.hotelpms.frontdesk.citytax.repository.HotelCategoryHistoryRepository;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentLineResult;
import com.hotelpms.frontdesk.citytax.service.CityTaxCalculator.CityTaxAssessmentResult;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.ChargeRequest;
import com.hotelpms.frontdesk.client.dto.ChargeResponse;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.stays.domain.CityTaxApplicability;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.impl.StayInvoiceResolver;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
    private static final long BACKFILL_NIGHTS = 1L;
    private static final BigDecimal RATE_AMOUNT_PER_NIGHT = new BigDecimal("2.50");
    private static final int LATER_DAYS_OFFSET = 5;
    private static final BigDecimal ASSESSMENT_TOTAL = new BigDecimal("15.00");
    private static final LocalDate PRIOR_YEAR_START = LocalDate.of(2025, 1, 1);
    private static final LocalDate NEW_YEAR_START = LocalDate.of(2026, 1, 1);

    @Mock
    private CityTaxAssessmentRepository cityTaxAssessmentRepository;

    @Mock
    private CityTaxAssessmentLineRepository cityTaxAssessmentLineRepository;

    @Mock
    private CityTaxRateRepository cityTaxRateRepository;

    @Mock
    private HotelCategoryHistoryRepository hotelCategoryHistoryRepository;

    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    @Mock
    private StayRepository stayRepository;

    @Mock
    private BillingClient billingClient;

    @Mock
    private CityTaxCalculator cityTaxCalculator;

    @Mock
    private StayInvoiceResolver stayInvoiceResolver;

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

    /**
     * A single-segment result covering the whole range at one rate — the common case.
     *
     * @param rateId         the rate's id
     * @param from           segment start (inclusive)
     * @param toExclusive    segment end (exclusive)
     * @param nights         taxable nights
     * @param guests         taxable guests
     * @param amountPerNight the rate's per-night amount
     * @param subtotal       the expected subtotal
     * @return a single-line assessment result
     */
    private static CityTaxAssessmentResult singleLineResult(
            final UUID rateId, final LocalDate from, final LocalDate toExclusive,
            final int nights, final int guests, final BigDecimal amountPerNight, final BigDecimal subtotal) {
        return new CityTaxAssessmentResult(nights, guests, subtotal, List.of(
                new CityTaxAssessmentLineResult(from, toExclusive, rateId, amountPerNight, guests, nights, subtotal)));
    }

    // -----------------------------------------------------------------
    // assessFor
    // -----------------------------------------------------------------

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
    void assessForHotelWithoutComuneConfiguredPersistsUnassessedRow() {
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.empty());
        when(cityTaxAssessmentRepository.save(any(CityTaxAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isPresent());
        assertEquals(CityTaxUnassessedReason.COMUNE_NOT_CONFIGURED, result.get().getUnassessedReason());
        assertEquals(BigDecimal.ZERO, result.get().getTotalAmount());
        verify(cityTaxAssessmentRepository).save(any(CityTaxAssessment.class));
        verify(cityTaxAssessmentLineRepository, never()).saveAll(any());
    }

    @Test
    void assessForHotelWithoutCategoryRecordedPersistsUnassessedRow() {
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId))
                .thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT)).thenReturn(Optional.empty());
        when(cityTaxAssessmentRepository.save(any(CityTaxAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isPresent());
        assertEquals(CityTaxUnassessedReason.CATEGORY_NOT_RECORDED, result.get().getUnassessedReason());
    }

    @Test
    void assessForNoApplicableRatePersistsUnassessedRow() {
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId))
                .thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(
                hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT, FIRST_NIGHT.plusDays(NIGHTS)))
                .thenReturn(List.of());
        when(cityTaxAssessmentRepository.save(any(CityTaxAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isPresent());
        assertEquals(CityTaxUnassessedReason.NO_RATE_FOR_DATE, result.get().getUnassessedReason());
    }

    @Test
    void assessForRateCoveringOnlyPartOfTheStayPersistsUnassessedRow() {
        // A rate exists but only covers the first night — the remaining nights have no
        // configuration. This must be treated the same as "nothing configured at all",
        // not as a partial assessment.
        final CityTaxRate partialRate = CityTaxRate.builder()
                .id(UUID.randomUUID()).comuneCodice(COMUNE_CODICE).category(CATEGORY)
                .amountPerNight(RATE_AMOUNT_PER_NIGHT).validFrom(FIRST_NIGHT).validTo(FIRST_NIGHT.plusDays(1)).build();
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId))
                .thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(
                hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT, FIRST_NIGHT.plusDays(NIGHTS)))
                .thenReturn(List.of(partialRate));
        when(cityTaxAssessmentRepository.save(any(CityTaxAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isPresent());
        assertEquals(CityTaxUnassessedReason.NO_RATE_FOR_DATE, result.get().getUnassessedReason());
        verify(cityTaxCalculator, never()).assess(any(), any(), anyLong(), any());
    }

    @Test
    void assessForHotelDeclaredNotApplicableSkipsResolutionAndPersistsUnassessedRow() {
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        final HotelSettings settings = settingsWithComune(COMUNE_CODICE);
        settings.setCityTaxApplicability(CityTaxApplicability.NOT_APPLICABLE);
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settings));
        when(cityTaxAssessmentRepository.save(any(CityTaxAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isPresent());
        assertEquals(CityTaxUnassessedReason.NOT_APPLICABLE, result.get().getUnassessedReason());
        verify(hotelCategoryHistoryRepository, never()).findApplicableByHotelId(any(), any());
    }

    @Test
    void assessForApplicableRateComputesAndPersistsSnapshotWithNullUnassessedReason() {
        final CityTaxRate rate = CityTaxRate.builder()
                .id(UUID.randomUUID())
                .hotelId(hotelId)
                .comuneCodice(COMUNE_CODICE)
                .category(CATEGORY)
                .amountPerNight(RATE_AMOUNT_PER_NIGHT)
                .maxTaxableNights(MAX_TAXABLE_NIGHTS)
                .exemptUnderAge(EXEMPT_UNDER_AGE)
                .validFrom(RATE_VALID_FROM)
                .build();
        final LocalDate stayEnd = FIRST_NIGHT.plusDays(NIGHTS);

        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId))
                .thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(
                hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT, stayEnd))
                .thenReturn(List.of(rate));
        when(cityTaxCalculator.assess(List.of(rate), FIRST_NIGHT, NIGHTS, stay.getGuests()))
                .thenReturn(singleLineResult(rate.getId(), FIRST_NIGHT, stayEnd, 3, 2, RATE_AMOUNT_PER_NIGHT, ASSESSMENT_TOTAL));
        when(cityTaxAssessmentRepository.save(any(CityTaxAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(stay, NIGHTS);

        assertTrue(result.isPresent());
        final CityTaxAssessment saved = result.get();
        assertEquals(hotelId, saved.getHotelId());
        assertEquals(stayId, saved.getStayId());
        assertEquals(rate.getId(), saved.getCityTaxRateId());
        assertEquals(RATE_AMOUNT_PER_NIGHT, saved.getAmountPerNightSnapshot());
        assertEquals(MAX_TAXABLE_NIGHTS, saved.getMaxTaxableNightsSnapshot());
        assertEquals(EXEMPT_UNDER_AGE, saved.getExemptUnderAgeSnapshot());
        assertEquals(2, saved.getTaxableGuests());
        assertEquals(3, saved.getTaxableNights());
        assertEquals(ASSESSMENT_TOTAL, saved.getTotalAmount());
        assertNull(saved.getUnassessedReason());

        final ArgumentCaptor<List<CityTaxAssessmentLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(cityTaxAssessmentLineRepository).saveAll(linesCaptor.capture());
        assertEquals(1, linesCaptor.getValue().size());
        assertEquals(ASSESSMENT_TOTAL, linesCaptor.getValue().get(0).getSubtotal());
    }

    @Test
    void assessForStayCrossingARateChangePersistsOneLinePerSegment() {
        // Parte 6: the calculator returns one line per rate actually in effect —
        // assessFor must persist all of them, and the aggregate total must be their sum.
        final CityTaxRate oldRate = CityTaxRate.builder()
                .id(UUID.randomUUID()).hotelId(hotelId).comuneCodice(COMUNE_CODICE).category(CATEGORY)
                .amountPerNight(new BigDecimal("2.00")).validFrom(PRIOR_YEAR_START)
                .validTo(NEW_YEAR_START).build();
        final CityTaxRate newRate = CityTaxRate.builder()
                .id(UUID.randomUUID()).hotelId(hotelId).comuneCodice(COMUNE_CODICE).category(CATEGORY)
                .amountPerNight(new BigDecimal("3.00")).validFrom(NEW_YEAR_START).build();
        final LocalDate checkIn = LocalDate.of(2025, 12, 30);
        final long crossoverNights = 4L;
        final LocalDate stayEnd = checkIn.plusDays(crossoverNights);
        final Stay crossoverStay = Stay.builder().id(stayId).hotelId(hotelId).actualCheckInTime(checkIn.atStartOfDay()).build();

        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        when(hotelSettingsRepository.findById(hotelId))
                .thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, checkIn))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(hotelId, COMUNE_CODICE, CATEGORY, checkIn, stayEnd))
                .thenReturn(List.of(oldRate, newRate));
        final CityTaxAssessmentResult twoLineResult = new CityTaxAssessmentResult(4, 1, new BigDecimal("10.00"), List.of(
                new CityTaxAssessmentLineResult(checkIn, LocalDate.of(2026, 1, 1), oldRate.getId(),
                        oldRate.getAmountPerNight(), 1, 2, new BigDecimal("4.00")),
                new CityTaxAssessmentLineResult(LocalDate.of(2026, 1, 1), stayEnd, newRate.getId(),
                        newRate.getAmountPerNight(), 1, 2, new BigDecimal("6.00"))));
        when(cityTaxCalculator.assess(List.of(oldRate, newRate), checkIn, crossoverNights, crossoverStay.getGuests()))
                .thenReturn(twoLineResult);
        when(cityTaxAssessmentRepository.save(any(CityTaxAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.assessFor(crossoverStay, crossoverNights);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("10.00"), result.get().getTotalAmount());
        // Snapshot fields reference the FIRST segment's rate, per the documented convention.
        assertEquals(oldRate.getId(), result.get().getCityTaxRateId());
        assertEquals(oldRate.getAmountPerNight(), result.get().getAmountPerNightSnapshot());

        final ArgumentCaptor<List<CityTaxAssessmentLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(cityTaxAssessmentLineRepository).saveAll(linesCaptor.capture());
        assertEquals(2, linesCaptor.getValue().size());
        assertEquals(oldRate.getId(), linesCaptor.getValue().get(0).getCityTaxRateId());
        assertEquals(newRate.getId(), linesCaptor.getValue().get(1).getCityTaxRateId());
        assertEquals(new BigDecimal("4.00"), linesCaptor.getValue().get(0).getSubtotal());
        assertEquals(new BigDecimal("6.00"), linesCaptor.getValue().get(1).getSubtotal());
    }

    // -----------------------------------------------------------------
    // rectifyForGuestAdded
    // -----------------------------------------------------------------

    @Test
    void rectifyForGuestAddedIsNoOpWhenStayHasNoAssessmentYet() {
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());
        final StayGuest newGuest = StayGuest.builder().id(UUID.randomUUID()).arrivalDate(FIRST_NIGHT).build();

        cityTaxAssessmentService.rectifyForGuestAdded(stay, newGuest);

        verify(cityTaxCalculator, never()).assess(any(), any(), anyLong(), any());
        verify(cityTaxAssessmentRepository, never()).save(any());
    }

    @Test
    void rectifyForGuestAddedIsNoOpWhenStayAssessmentIsUnconfigured() {
        final CityTaxAssessment unassessed = CityTaxAssessment.builder()
                .id(UUID.randomUUID()).unassessedReason(CityTaxUnassessedReason.NO_RATE_FOR_DATE).build();
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(unassessed));
        final StayGuest newGuest = StayGuest.builder().id(UUID.randomUUID()).arrivalDate(FIRST_NIGHT).build();

        cityTaxAssessmentService.rectifyForGuestAdded(stay, newGuest);

        verify(cityTaxCalculator, never()).assess(any(), any(), anyLong(), any());
    }

    @Test
    void rectifyForGuestAddedAddsLineAndChargeWhenInvoiceOpen() {
        final CityTaxAssessment assessment = CityTaxAssessment.builder()
                .id(UUID.randomUUID()).stayId(stayId).hotelId(hotelId).totalAmount(ASSESSMENT_TOTAL).build();
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(assessment));

        final StayGuest newGuest = StayGuest.builder().id(UUID.randomUUID()).arrivalDate(FIRST_NIGHT).build();
        final long remainingNights = 2L;
        stay.setExpectedCheckOutDate(FIRST_NIGHT.plusDays(remainingNights));
        final CityTaxRate rate = CityTaxRate.builder()
                .id(UUID.randomUUID()).amountPerNight(RATE_AMOUNT_PER_NIGHT).validFrom(RATE_VALID_FROM).build();
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(
                hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT, FIRST_NIGHT.plusDays(remainingNights)))
                .thenReturn(List.of(rate));
        final BigDecimal rectificationAmount = new BigDecimal("5.00");
        when(cityTaxCalculator.assess(List.of(rate), FIRST_NIGHT, remainingNights, List.of(newGuest)))
                .thenReturn(singleLineResult(
                        rate.getId(), FIRST_NIGHT, FIRST_NIGHT.plusDays(remainingNights),
                        (int) remainingNights, 1, RATE_AMOUNT_PER_NIGHT, rectificationAmount));
        stay.setInvoiceId(UUID.randomUUID());
        when(stayInvoiceResolver.isOpen(stay)).thenReturn(true);
        when(billingClient.addCharge(eq(stayId), any(ChargeRequest.class))).thenReturn(new ChargeResponse(UUID.randomUUID()));

        cityTaxAssessmentService.rectifyForGuestAdded(stay, newGuest);

        assertEquals(ASSESSMENT_TOTAL.add(rectificationAmount), assessment.getTotalAmount());
        verify(cityTaxAssessmentRepository).save(assessment);
        verify(cityTaxAssessmentLineRepository).saveAll(any());
        verify(billingClient).addCharge(eq(stayId), any(ChargeRequest.class));
    }

    @Test
    void rectifyForGuestAddedSkipsChargeButKeepsLineWhenInvoiceNotOpen() {
        final CityTaxAssessment assessment = CityTaxAssessment.builder()
                .id(UUID.randomUUID()).stayId(stayId).hotelId(hotelId).totalAmount(ASSESSMENT_TOTAL).build();
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(assessment));

        final StayGuest newGuest = StayGuest.builder().id(UUID.randomUUID()).arrivalDate(FIRST_NIGHT).build();
        final long remainingNights = 1L;
        stay.setExpectedCheckOutDate(FIRST_NIGHT.plusDays(remainingNights));
        final CityTaxRate rate = CityTaxRate.builder()
                .id(UUID.randomUUID()).amountPerNight(RATE_AMOUNT_PER_NIGHT).validFrom(RATE_VALID_FROM).build();
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(
                hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT, FIRST_NIGHT.plusDays(remainingNights)))
                .thenReturn(List.of(rate));
        when(cityTaxCalculator.assess(List.of(rate), FIRST_NIGHT, remainingNights, List.of(newGuest)))
                .thenReturn(singleLineResult(
                        rate.getId(), FIRST_NIGHT, FIRST_NIGHT.plusDays(remainingNights),
                        (int) remainingNights, 1, RATE_AMOUNT_PER_NIGHT, RATE_AMOUNT_PER_NIGHT));
        stay.setInvoiceId(null);

        cityTaxAssessmentService.rectifyForGuestAdded(stay, newGuest);

        assertEquals(ASSESSMENT_TOTAL.add(RATE_AMOUNT_PER_NIGHT), assessment.getTotalAmount());
        verify(cityTaxAssessmentLineRepository).saveAll(any());
        verify(billingClient, never()).addCharge(any(), any());
    }

    // -----------------------------------------------------------------
    // findAssessment / markCharged
    // -----------------------------------------------------------------

    @Test
    void findAssessmentReturnsWhateverTheRepositoryHasWithoutSideEffects() {
        final CityTaxAssessment existing = CityTaxAssessment.builder().id(UUID.randomUUID()).build();
        when(cityTaxAssessmentRepository.findByStayIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(existing));

        final Optional<CityTaxAssessment> result = cityTaxAssessmentService.findAssessment(stayId, hotelId);

        assertEquals(existing, result.orElseThrow());
        verify(cityTaxAssessmentRepository, never()).save(any());
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

    // -----------------------------------------------------------------
    // checkConfigurationStatus — single date (today), unaffected by Parte 6
    // -----------------------------------------------------------------

    @Test
    void checkConfigurationStatusWithApplicableRateIsConfigured() {
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(eq(hotelId), any(LocalDate.class)))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findApplicableByHotelId(eq(hotelId), eq(COMUNE_CODICE), eq(CATEGORY), any(LocalDate.class)))
                .thenReturn(Optional.of(CityTaxRate.builder().id(UUID.randomUUID()).build()));

        final CityTaxConfigurationStatusResponse status = cityTaxAssessmentService.checkConfigurationStatus(hotelId);

        assertTrue(status.configured());
        assertNull(status.reason());
    }

    @Test
    void checkConfigurationStatusWithoutComuneIsNotConfigured() {
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.empty());

        final CityTaxConfigurationStatusResponse status = cityTaxAssessmentService.checkConfigurationStatus(hotelId);

        assertFalse(status.configured());
        assertEquals(CityTaxUnassessedReason.COMUNE_NOT_CONFIGURED, status.reason());
    }

    @Test
    void checkConfigurationStatusDeclaredNotApplicableIsConfiguredWithNoReason() {
        final HotelSettings settings = settingsWithComune(COMUNE_CODICE);
        settings.setCityTaxApplicability(CityTaxApplicability.NOT_APPLICABLE);
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settings));

        final CityTaxConfigurationStatusResponse status = cityTaxAssessmentService.checkConfigurationStatus(hotelId);

        assertTrue(status.configured());
        assertNull(status.reason());
        verify(hotelCategoryHistoryRepository, never()).findApplicableByHotelId(any(), any());
    }

    // -----------------------------------------------------------------
    // getUnassessedSummary
    // -----------------------------------------------------------------

    @Test
    void unassessedSummaryReturnsCountAndMostRecent() {
        final CityTaxAssessment older = CityTaxAssessment.builder()
                .assessedAt(FIRST_NIGHT.atStartOfDay())
                .unassessedReason(CityTaxUnassessedReason.NO_RATE_FOR_DATE)
                .build();
        final CityTaxAssessment newer = CityTaxAssessment.builder()
                .assessedAt(FIRST_NIGHT.plusDays(LATER_DAYS_OFFSET).atStartOfDay())
                .unassessedReason(CityTaxUnassessedReason.COMUNE_NOT_CONFIGURED)
                .build();
        when(cityTaxAssessmentRepository.findByHotelIdAndUnassessedReasonIn(eq(hotelId), any()))
                .thenReturn(List.of(older, newer));

        final CityTaxUnassessedSummaryResponse summary = cityTaxAssessmentService.getUnassessedSummary(hotelId);

        assertEquals(2, summary.unassessedCount());
        assertEquals(newer.getAssessedAt(), summary.mostRecentUnassessedAt());
        assertEquals(CityTaxUnassessedReason.COMUNE_NOT_CONFIGURED, summary.mostRecentReason());
    }

    @Test
    void unassessedSummaryWithNoGapsReturnsZeroAndNulls() {
        when(cityTaxAssessmentRepository.findByHotelIdAndUnassessedReasonIn(eq(hotelId), any()))
                .thenReturn(List.of());

        final CityTaxUnassessedSummaryResponse summary = cityTaxAssessmentService.getUnassessedSummary(hotelId);

        assertEquals(0, summary.unassessedCount());
        assertNull(summary.mostRecentUnassessedAt());
        assertNull(summary.mostRecentReason());
    }

    // -----------------------------------------------------------------
    // preview/confirm backfill
    // -----------------------------------------------------------------

    @Test
    void previewBackfillNeverWritesOrCharges() {
        final CityTaxRate rate = backfillableRate();
        final CityTaxAssessment gap = gapAssessment(CityTaxUnassessedReason.NO_RATE_FOR_DATE);
        final LocalDate backfillStayEnd = FIRST_NIGHT.plusDays(BACKFILL_NIGHTS);
        when(cityTaxAssessmentRepository.findByHotelIdAndUnassessedReasonIn(eq(hotelId), any()))
                .thenReturn(List.of(gap));
        when(stayRepository.findAllById(any())).thenReturn(List.of(stay));
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(
                hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT, backfillStayEnd))
                .thenReturn(List.of(rate));
        when(cityTaxCalculator.assess(eq(List.of(rate)), eq(FIRST_NIGHT), anyLong(), any()))
                .thenReturn(singleLineResult(
                        rate.getId(), FIRST_NIGHT, backfillStayEnd, 1, 1, RATE_AMOUNT_PER_NIGHT, RATE_AMOUNT_PER_NIGHT));
        stay.setInvoiceId(UUID.randomUUID());
        when(stayInvoiceResolver.isOpen(stay)).thenReturn(true);

        final CityTaxBackfillResponse preview = cityTaxAssessmentService.previewBackfill(hotelId);

        assertEquals(1, preview.lines().size());
        assertFalse(preview.lines().get(0).charged());
        assertEquals(RATE_AMOUNT_PER_NIGHT, preview.totalAmount());
        assertEquals(0, preview.chargedCount());
        verify(billingClient, never()).addCharge(any(), any());
        verify(cityTaxAssessmentRepository, never()).save(any());
        verify(cityTaxAssessmentLineRepository, never()).saveAll(any());
    }

    @Test
    void confirmBackfillChargesOpenInvoiceAndCorrectsAssessment() {
        final CityTaxRate rate = backfillableRate();
        final CityTaxAssessment gap = gapAssessment(CityTaxUnassessedReason.NO_RATE_FOR_DATE);
        final LocalDate backfillStayEnd = FIRST_NIGHT.plusDays(BACKFILL_NIGHTS);
        when(cityTaxAssessmentRepository.findByHotelIdAndUnassessedReasonIn(eq(hotelId), any()))
                .thenReturn(List.of(gap));
        when(stayRepository.findAllById(any())).thenReturn(List.of(stay));
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(
                hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT, backfillStayEnd))
                .thenReturn(List.of(rate));
        when(cityTaxCalculator.assess(eq(List.of(rate)), eq(FIRST_NIGHT), anyLong(), any()))
                .thenReturn(singleLineResult(
                        rate.getId(), FIRST_NIGHT, backfillStayEnd, 1, 1, RATE_AMOUNT_PER_NIGHT, RATE_AMOUNT_PER_NIGHT));
        stay.setInvoiceId(UUID.randomUUID());
        when(stayInvoiceResolver.isOpen(stay)).thenReturn(true);
        final UUID chargeId = UUID.randomUUID();
        when(billingClient.addCharge(eq(stayId), any(ChargeRequest.class))).thenReturn(new ChargeResponse(chargeId));
        when(cityTaxAssessmentRepository.save(gap)).thenReturn(gap);

        final CityTaxBackfillResponse result = cityTaxAssessmentService.confirmBackfill(hotelId);

        assertEquals(1, result.chargedCount());
        assertEquals(0, result.skippedCount());
        assertTrue(result.lines().get(0).charged());
        assertEquals(chargeId, gap.getBillingChargeId());
        assertNull(gap.getUnassessedReason());
        assertEquals(RATE_AMOUNT_PER_NIGHT, gap.getTotalAmount());
        verify(cityTaxAssessmentRepository).save(gap);
        verify(cityTaxAssessmentLineRepository).saveAll(any());
    }

    @Test
    void confirmBackfillSkipsStayWithClosedInvoiceWithoutCharging() {
        final CityTaxRate rate = backfillableRate();
        final CityTaxAssessment gap = gapAssessment(CityTaxUnassessedReason.NO_RATE_FOR_DATE);
        final LocalDate backfillStayEnd = FIRST_NIGHT.plusDays(BACKFILL_NIGHTS);
        when(cityTaxAssessmentRepository.findByHotelIdAndUnassessedReasonIn(eq(hotelId), any()))
                .thenReturn(List.of(gap));
        when(stayRepository.findAllById(any())).thenReturn(List.of(stay));
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(
                hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT, backfillStayEnd))
                .thenReturn(List.of(rate));
        when(cityTaxCalculator.assess(eq(List.of(rate)), eq(FIRST_NIGHT), anyLong(), any()))
                .thenReturn(singleLineResult(
                        rate.getId(), FIRST_NIGHT, backfillStayEnd, 1, 1, RATE_AMOUNT_PER_NIGHT, RATE_AMOUNT_PER_NIGHT));
        stay.setInvoiceId(UUID.randomUUID());
        when(stayInvoiceResolver.isOpen(stay)).thenReturn(false);

        final CityTaxBackfillResponse result = cityTaxAssessmentService.confirmBackfill(hotelId);

        assertEquals(0, result.chargedCount());
        assertEquals(1, result.skippedCount());
        assertEquals("INVOICE_NOT_OPEN", result.lines().get(0).skipReason());
        verify(billingClient, never()).addCharge(any(), any());
        verify(cityTaxAssessmentRepository, never()).save(any());
    }

    @Test
    void confirmBackfillSkipsStayStillLackingConfigurationForItsOwnDate() {
        final CityTaxAssessment gap = gapAssessment(CityTaxUnassessedReason.NO_RATE_FOR_DATE);
        when(cityTaxAssessmentRepository.findByHotelIdAndUnassessedReasonIn(eq(hotelId), any()))
                .thenReturn(List.of(gap));
        when(stayRepository.findAllById(any())).thenReturn(List.of(stay));
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT)).thenReturn(Optional.empty());

        final CityTaxBackfillResponse result = cityTaxAssessmentService.confirmBackfill(hotelId);

        assertEquals(0, result.chargedCount());
        assertEquals(1, result.skippedCount());
        assertEquals("STILL_UNCONFIGURED", result.lines().get(0).skipReason());
        verify(stayInvoiceResolver, never()).isOpen(any());
        verify(billingClient, never()).addCharge(any(), any());
    }

    @Test
    void confirmBackfillDoesNothingWhenHotelHasSinceDeclaredNotApplicable() {
        final HotelSettings settings = settingsWithComune(COMUNE_CODICE);
        settings.setCityTaxApplicability(CityTaxApplicability.NOT_APPLICABLE);
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settings));

        final CityTaxBackfillResponse result = cityTaxAssessmentService.confirmBackfill(hotelId);

        assertTrue(result.lines().isEmpty());
        assertEquals(0, result.chargedCount());
        verify(cityTaxAssessmentRepository, never()).findByHotelIdAndUnassessedReasonIn(any(), any());
    }

    @Test
    void confirmBackfillSkipsWhenBillingServiceRejectsTheCharge() {
        final CityTaxRate rate = backfillableRate();
        final CityTaxAssessment gap = gapAssessment(CityTaxUnassessedReason.NO_RATE_FOR_DATE);
        final LocalDate backfillStayEnd = FIRST_NIGHT.plusDays(BACKFILL_NIGHTS);
        when(cityTaxAssessmentRepository.findByHotelIdAndUnassessedReasonIn(eq(hotelId), any()))
                .thenReturn(List.of(gap));
        when(stayRepository.findAllById(any())).thenReturn(List.of(stay));
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(hotelCategoryHistoryRepository.findApplicableByHotelId(hotelId, FIRST_NIGHT))
                .thenReturn(Optional.of(categoryHistory(CATEGORY)));
        when(cityTaxRateRepository.findAllApplicableByHotelIdInRange(
                hotelId, COMUNE_CODICE, CATEGORY, FIRST_NIGHT, backfillStayEnd))
                .thenReturn(List.of(rate));
        when(cityTaxCalculator.assess(eq(List.of(rate)), eq(FIRST_NIGHT), anyLong(), any()))
                .thenReturn(singleLineResult(
                        rate.getId(), FIRST_NIGHT, backfillStayEnd, 1, 1, RATE_AMOUNT_PER_NIGHT, RATE_AMOUNT_PER_NIGHT));
        stay.setInvoiceId(UUID.randomUUID());
        when(stayInvoiceResolver.isOpen(stay)).thenReturn(true);
        when(billingClient.addCharge(eq(stayId), any(ChargeRequest.class)))
                .thenThrow(mock(FeignException.Conflict.class));

        final CityTaxBackfillResponse result = cityTaxAssessmentService.confirmBackfill(hotelId);

        assertEquals(0, result.chargedCount());
        assertEquals(1, result.skippedCount());
        assertEquals("CHARGE_FAILED", result.lines().get(0).skipReason());
        verify(cityTaxAssessmentRepository, never()).save(any());
    }

    private static HotelSettings settingsWithComune(final String comuneCodice) {
        final HotelSettings settings = new HotelSettings();
        settings.setComuneCodice(comuneCodice);
        return settings;
    }

    private static HotelCategoryHistory categoryHistory(final String category) {
        return HotelCategoryHistory.builder().id(UUID.randomUUID()).category(category).build();
    }

    private static CityTaxRate backfillableRate() {
        return CityTaxRate.builder()
                .id(UUID.randomUUID())
                .comuneCodice(COMUNE_CODICE)
                .category(CATEGORY)
                .amountPerNight(RATE_AMOUNT_PER_NIGHT)
                .validFrom(RATE_VALID_FROM)
                .build();
    }

    private CityTaxAssessment gapAssessment(final CityTaxUnassessedReason reason) {
        return CityTaxAssessment.builder()
                .id(UUID.randomUUID())
                .hotelId(hotelId)
                .stayId(stayId)
                .amountPerNightSnapshot(BigDecimal.ZERO)
                .taxableGuests(0)
                .taxableNights(0)
                .totalAmount(BigDecimal.ZERO)
                .assessedAt(FIRST_NIGHT.atStartOfDay())
                .unassessedReason(reason)
                .build();
    }
}
