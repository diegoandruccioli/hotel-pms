package com.hotelpms.frontdesk.citytax.service.impl;

import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryRequest;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryResponse;
import com.hotelpms.frontdesk.citytax.mapper.HotelCategoryHistoryMapper;
import com.hotelpms.frontdesk.citytax.repository.HotelCategoryHistoryRepository;
import com.hotelpms.frontdesk.citytax.service.HotelCategoryHistoryService;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of {@link HotelCategoryHistoryService}.
 */
@Service
@RequiredArgsConstructor
public class HotelCategoryHistoryServiceImpl implements HotelCategoryHistoryService {

    /** PostgreSQL SQLState for an EXCLUDE constraint violation. */
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";
    private static final String VALID_FROM_NOT_AFTER_CURRENT_MSG = "HOTEL_CATEGORY_VALID_FROM_NOT_AFTER_CURRENT";

    private final HotelCategoryHistoryRepository hotelCategoryHistoryRepository;
    private final HotelCategoryHistoryMapper hotelCategoryHistoryMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<HotelCategoryHistoryResponse> listHistory(final UUID hotelId) {
        Objects.requireNonNull(hotelId, "Hotel ID cannot be null");
        return hotelCategoryHistoryRepository.findAllByHotelIdOrderByValidFromDesc(hotelId).stream()
                .map(hotelCategoryHistoryMapper::toResponse)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public HotelCategoryHistoryResponse recordCategory(final UUID hotelId, final HotelCategoryHistoryRequest request) {
        Objects.requireNonNull(hotelId, "Hotel ID cannot be null");

        hotelCategoryHistoryRepository.findByHotelIdAndValidToIsNull(hotelId)
                .ifPresent(open -> {
                    if (!request.validFrom().isAfter(open.getValidFrom())) {
                        throw new BadRequestException(VALID_FROM_NOT_AFTER_CURRENT_MSG);
                    }
                    open.setValidTo(request.validFrom());
                    // saveAndFlush, not save: see CityTaxRateAdminServiceImpl.createRule for
                    // why a plain save() here lets Hibernate insert the new row before this
                    // close reaches the DB, tripping excl_hotel_category_no_overlap (V16)
                    // with a spurious 409 on a legitimate category change.
                    hotelCategoryHistoryRepository.saveAndFlush(open);
                });

        final HotelCategoryHistory entry = HotelCategoryHistory.builder()
                .hotelId(hotelId)
                .category(request.category())
                .validFrom(request.validFrom())
                .build();

        return hotelCategoryHistoryMapper.toResponse(saveTranslatingOverlap(entry));
    }

    private HotelCategoryHistory saveTranslatingOverlap(final HotelCategoryHistory entry) {
        try {
            return hotelCategoryHistoryRepository.saveAndFlush(entry);
        } catch (final DataIntegrityViolationException ex) {
            if (isExclusionViolation(ex)) {
                throw new ConflictException("HOTEL_CATEGORY_OVERLAP", ex);
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
