package com.hotelpms.frontdesk.citytax.service.impl;

import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateRequest;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateResponse;
import com.hotelpms.frontdesk.citytax.mapper.CityTaxRateMapper;
import com.hotelpms.frontdesk.citytax.repository.CityTaxRateRepository;
import com.hotelpms.frontdesk.citytax.service.CityTaxRateAdminService;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of {@link CityTaxRateAdminService}.
 */
@Service
@RequiredArgsConstructor
public class CityTaxRateAdminServiceImpl implements CityTaxRateAdminService {

    private static final String COMUNE_NOT_CONFIGURED_MSG = "CITY_TAX_COMUNE_NOT_CONFIGURED";
    private static final String VALID_FROM_NOT_AFTER_CURRENT_MSG = "CITY_TAX_RATE_VALID_FROM_NOT_AFTER_CURRENT";
    /** PostgreSQL SQLState for an EXCLUDE constraint violation — same convention as RateSeasonAdminServiceImpl. */
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    private final CityTaxRateRepository cityTaxRateRepository;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final CityTaxRateMapper cityTaxRateMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<CityTaxRateResponse> listRules(final UUID hotelId) {
        Objects.requireNonNull(hotelId, "Hotel ID cannot be null");
        return cityTaxRateRepository.findAllByHotelIdOrderByValidFromDesc(hotelId).stream()
                .map(cityTaxRateMapper::toResponse)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CityTaxRateResponse createRule(final UUID hotelId, final CityTaxRateRequest request) {
        Objects.requireNonNull(hotelId, "Hotel ID cannot be null");
        final String comuneCodice = hotelSettingsRepository.findById(hotelId)
                .map(HotelSettings::getComuneCodice)
                .filter(code -> !code.isBlank())
                .orElseThrow(() -> new BadRequestException(COMUNE_NOT_CONFIGURED_MSG));

        cityTaxRateRepository
                .findByHotelIdAndComuneCodiceAndCategoryAndValidToIsNull(hotelId, comuneCodice, request.category())
                .ifPresent(open -> {
                    if (!request.validFrom().isAfter(open.getValidFrom())) {
                        throw new BadRequestException(VALID_FROM_NOT_AFTER_CURRENT_MSG);
                    }
                    open.setValidTo(request.validFrom());
                    // saveAndFlush, not save: the auto-close must hit the DB before the new
                    // row is inserted below. Hibernate's ActionQueue runs INSERTs before
                    // UPDATEs regardless of call order, so a plain save() here left the old
                    // row's valid_to = NULL at INSERT time — the two open-ended ranges
                    // overlapped and excl_city_tax_rates_no_overlap (V17) rejected the new
                    // rate with a spurious 409, even though the request was legitimate.
                    cityTaxRateRepository.saveAndFlush(open);
                });

        final CityTaxRate rate = CityTaxRate.builder()
                .hotelId(hotelId)
                .comuneCodice(comuneCodice)
                .category(request.category())
                .amountPerNight(request.amountPerNight())
                .maxTaxableNights(request.maxTaxableNights())
                .exemptUnderAge(request.exemptUnderAge())
                .validFrom(request.validFrom())
                .note(request.note())
                .build();

        return cityTaxRateMapper.toResponse(saveTranslatingOverlap(rate));
    }

    /**
     * Saves a rate rule, translating a DB-level {@code excl_city_tax_rates_no_overlap}
     * violation into a clear application 409 — same pattern as
     * {@code RateSeasonAdminServiceImpl.saveTranslatingOverlap}.
     *
     * @param rate the rule to persist
     * @return the persisted rule
     */
    private CityTaxRate saveTranslatingOverlap(final CityTaxRate rate) {
        try {
            return cityTaxRateRepository.saveAndFlush(rate);
        } catch (final DataIntegrityViolationException ex) {
            if (isExclusionViolation(ex)) {
                throw new ConflictException("CITY_TAX_RATE_OVERLAP", ex);
            }
            throw ex;
        }
    }

    private static boolean isExclusionViolation(final DataIntegrityViolationException ex) {
        final Throwable cause = ex.getMostSpecificCause();
        return cause instanceof final SQLException sqlException
                && SQLSTATE_EXCLUSION_VIOLATION.equals(sqlException.getSQLState());
    }
}
