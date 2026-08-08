package com.hotelpms.frontdesk.pricing.service.impl;

import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonRequest;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;
import com.hotelpms.frontdesk.pricing.mapper.RateSeasonMapper;
import com.hotelpms.frontdesk.pricing.repository.RateSeasonRepository;
import com.hotelpms.frontdesk.pricing.service.RateSeasonAdminService;
import com.hotelpms.frontdesk.rooms.service.RoomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of {@link RateSeasonAdminService}.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:DesignForExtension")
public class RateSeasonAdminServiceImpl implements RateSeasonAdminService {

    private static final String SEASON_NOT_FOUND_MSG = "RATE_SEASON_NOT_FOUND";
    private static final String HOTEL_ID_NULL_MSG = "Hotel ID cannot be null";
    private static final String ROOM_TYPE_ID_NULL_MSG = "Room type ID cannot be null";
    private static final String SEASON_ID_NULL_MSG = "Season ID cannot be null";
    /** PostgreSQL SQLState for an EXCLUDE constraint violation. */
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    private final RateSeasonRepository rateSeasonRepository;
    private final RateSeasonMapper rateSeasonMapper;
    private final RoomTypeService roomTypeService;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<RateSeasonResponse> listSeasons(final UUID roomTypeId, final UUID hotelId) {
        Objects.requireNonNull(roomTypeId, ROOM_TYPE_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        // Confirms the room type belongs to this hotel (T-ROOM-02) — a wrong-tenant
        // roomTypeId would otherwise just return an empty list instead of a clear 404.
        roomTypeService.getRoomTypeById(roomTypeId, hotelId);
        return rateSeasonRepository.findAllByRoomTypeIdAndHotelIdOrderByStartDateAsc(roomTypeId, hotelId).stream()
                .map(rateSeasonMapper::toResponse)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RateSeasonResponse createSeason(
            final UUID roomTypeId, final UUID hotelId, final RateSeasonRequest request) {
        Objects.requireNonNull(roomTypeId, ROOM_TYPE_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        roomTypeService.getRoomTypeById(roomTypeId, hotelId);

        final RateSeason season = rateSeasonMapper.toEntity(request);
        season.setRoomTypeId(roomTypeId);
        season.setHotelId(hotelId);
        final RateSeason saved = saveTranslatingOverlap(season);
        return rateSeasonMapper.toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RateSeasonResponse updateSeason(final UUID id, final UUID hotelId, final RateSeasonRequest request) {
        Objects.requireNonNull(id, SEASON_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final RateSeason season = rateSeasonRepository.findByIdAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(SEASON_NOT_FOUND_MSG));

        rateSeasonMapper.updateEntityFromRequest(request, season);
        final RateSeason updated = saveTranslatingOverlap(season);
        return rateSeasonMapper.toResponse(updated);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteSeason(final UUID id, final UUID hotelId) {
        Objects.requireNonNull(id, SEASON_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final RateSeason season = rateSeasonRepository.findByIdAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(SEASON_NOT_FOUND_MSG));
        rateSeasonRepository.delete(season); // Triggers the @SQLDelete soft delete
    }

    /**
     * Saves a season, translating a DB-level {@code excl_rate_seasons_no_overlap}
     * violation into a clear application 409 instead of the generic "resource
     * already exists" mapping {@code GlobalExceptionHandler} would otherwise
     * apply to any {@link DataIntegrityViolationException}.
     *
     * @param season the season to persist
     * @return the persisted season
     */
    private RateSeason saveTranslatingOverlap(final RateSeason season) {
        try {
            return rateSeasonRepository.saveAndFlush(season);
        } catch (final DataIntegrityViolationException ex) {
            if (isExclusionViolation(ex)) {
                throw new ConflictException("RATE_SEASON_OVERLAP", ex);
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
