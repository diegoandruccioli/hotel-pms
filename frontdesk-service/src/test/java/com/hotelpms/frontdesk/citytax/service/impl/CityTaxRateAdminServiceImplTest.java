package com.hotelpms.frontdesk.citytax.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateRequest;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateResponse;
import com.hotelpms.frontdesk.citytax.mapper.CityTaxRateMapper;
import com.hotelpms.frontdesk.citytax.repository.CityTaxRateRepository;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityTaxRateAdminServiceImplTest {

    private static final String COMUNE_CODICE = "099014";
    private static final String CATEGORY = "4_STAR";
    private static final int MAX_TAXABLE_NIGHTS = 7;
    private static final int EXEMPT_UNDER_AGE = 14;
    private static final LocalDate RATE_VALID_FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate OLD_RATE_VALID_FROM = LocalDate.of(2025, 1, 1);
    /** PostgreSQL SQLState for an EXCLUDE constraint violation. */
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    @Mock
    private CityTaxRateRepository cityTaxRateRepository;

    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    @Mock
    private CityTaxRateMapper cityTaxRateMapper;

    @InjectMocks
    private CityTaxRateAdminServiceImpl cityTaxRateAdminService;

    private UUID hotelId;
    private CityTaxRateRequest request;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();
        request = new CityTaxRateRequest(CATEGORY, new BigDecimal("2.50"), MAX_TAXABLE_NIGHTS, EXEMPT_UNDER_AGE,
                RATE_VALID_FROM, "Delibera G.C. n. 45");
    }

    @Test
    void listRulesReturnsMappedRules() {
        final CityTaxRate rate = CityTaxRate.builder().id(UUID.randomUUID()).hotelId(hotelId).build();
        final CityTaxRateResponse response = mock(rate);
        when(cityTaxRateRepository.findAllByHotelIdOrderByValidFromDesc(hotelId)).thenReturn(List.of(rate));
        when(cityTaxRateMapper.toResponse(rate)).thenReturn(response);

        final List<CityTaxRateResponse> result = cityTaxRateAdminService.listRules(hotelId);

        assertEquals(1, result.size());
    }

    @Test
    void createRuleWithoutComuneConfiguredThrowsBadRequest() {
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> cityTaxRateAdminService.createRule(hotelId, request));
        verify(cityTaxRateRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRuleWithBlankComuneCodiceThrowsBadRequest() {
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune("")));

        assertThrows(BadRequestException.class, () -> cityTaxRateAdminService.createRule(hotelId, request));
    }

    @Test
    void createRuleResolvesComuneServerSideAndPersists() {
        final CityTaxRate saved = CityTaxRate.builder().id(UUID.randomUUID()).hotelId(hotelId).build();
        final CityTaxRateResponse response = mock(saved);
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(cityTaxRateRepository.findByHotelIdAndComuneCodiceAndCategoryAndValidToIsNull(
                hotelId, COMUNE_CODICE, CATEGORY)).thenReturn(Optional.empty());
        when(cityTaxRateRepository.saveAndFlush(any(CityTaxRate.class))).thenReturn(saved);
        when(cityTaxRateMapper.toResponse(saved)).thenReturn(response);

        final CityTaxRateResponse result = cityTaxRateAdminService.createRule(hotelId, request);

        assertEquals(response, result);
        final ArgumentCaptor<CityTaxRate> captor = ArgumentCaptor.forClass(CityTaxRate.class);
        verify(cityTaxRateRepository).saveAndFlush(captor.capture());
        assertEquals(hotelId, captor.getValue().getHotelId());
        assertEquals(COMUNE_CODICE, captor.getValue().getComuneCodice());
        assertEquals(CATEGORY, captor.getValue().getCategory());
    }

    @Test
    void createRuleClosesExistingOpenEndedRuleForSameComuneAndCategory() {
        final CityTaxRate openRule = CityTaxRate.builder()
                .id(UUID.randomUUID()).hotelId(hotelId).comuneCodice(COMUNE_CODICE).category(CATEGORY)
                .validFrom(OLD_RATE_VALID_FROM).build();
        final CityTaxRate saved = CityTaxRate.builder().id(UUID.randomUUID()).build();
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(cityTaxRateRepository.findByHotelIdAndComuneCodiceAndCategoryAndValidToIsNull(
                hotelId, COMUNE_CODICE, CATEGORY)).thenReturn(Optional.of(openRule));
        when(cityTaxRateRepository.saveAndFlush(any(CityTaxRate.class))).thenReturn(saved);
        when(cityTaxRateMapper.toResponse(saved)).thenReturn(mock(saved));

        cityTaxRateAdminService.createRule(hotelId, request);

        assertEquals(request.validFrom(), openRule.getValidTo());
        verify(cityTaxRateRepository).save(openRule);
    }

    @Test
    void createRuleOverlappingAnExistingRuleThrowsConflict() {
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(cityTaxRateRepository.findByHotelIdAndComuneCodiceAndCategoryAndValidToIsNull(
                hotelId, COMUNE_CODICE, CATEGORY)).thenReturn(Optional.empty());
        when(cityTaxRateRepository.saveAndFlush(any(CityTaxRate.class))).thenThrow(exclusionViolation());

        assertThrows(ConflictException.class, () -> cityTaxRateAdminService.createRule(hotelId, request));
    }

    @Test
    void createRuleWithUnrelatedDataIntegrityViolationRethrowsOriginal() {
        final DataIntegrityViolationException notNullViolation =
                new DataIntegrityViolationException("not null violation");
        when(hotelSettingsRepository.findById(hotelId)).thenReturn(Optional.of(settingsWithComune(COMUNE_CODICE)));
        when(cityTaxRateRepository.findByHotelIdAndComuneCodiceAndCategoryAndValidToIsNull(
                hotelId, COMUNE_CODICE, CATEGORY)).thenReturn(Optional.empty());
        when(cityTaxRateRepository.saveAndFlush(any(CityTaxRate.class))).thenThrow(notNullViolation);

        final DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> cityTaxRateAdminService.createRule(hotelId, request));
        assertEquals(notNullViolation, thrown);
    }

    private static HotelSettings settingsWithComune(final String comuneCodice) {
        final HotelSettings settings = new HotelSettings();
        settings.setComuneCodice(comuneCodice);
        return settings;
    }

    private static CityTaxRateResponse mock(final CityTaxRate rate) {
        return new CityTaxRateResponse(rate.getId(), rate.getComuneCodice(), rate.getCategory(),
                rate.getAmountPerNight(), rate.getMaxTaxableNights(), rate.getExemptUnderAge(),
                rate.getValidFrom(), rate.getValidTo(), rate.getNote());
    }

    private static DataIntegrityViolationException exclusionViolation() {
        final SQLException sqlException = new SQLException("overlap", SQLSTATE_EXCLUSION_VIOLATION);
        return new DataIntegrityViolationException("excl_city_tax_rates_no_overlap", sqlException);
    }
}
