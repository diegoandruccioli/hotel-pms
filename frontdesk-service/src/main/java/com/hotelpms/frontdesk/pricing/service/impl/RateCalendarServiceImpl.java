package com.hotelpms.frontdesk.pricing.service.impl;

import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import com.hotelpms.frontdesk.pricing.dto.RateBulkApplyRequest;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarDay;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarResponse;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarRow;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;
import com.hotelpms.frontdesk.pricing.mapper.RateSeasonMapper;
import com.hotelpms.frontdesk.pricing.repository.RateSeasonRepository;
import com.hotelpms.frontdesk.pricing.service.RateCalendarService;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link RateCalendarService}. The read side never
 * issues one query per night: it loads every room type once and every
 * overlapping season for the whole hotel once, then resolves each day in
 * memory. The write side (bulk apply) accepts a range that collides with
 * existing seasons and reshapes them (split/trim) instead of rejecting —
 * {@link RateSeasonAdminServiceImpl} is the single-season, reject-on-overlap
 * counterpart used by the season detail form.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:DesignForExtension")
public class RateCalendarServiceImpl implements RateCalendarService {

    private static final String HOTEL_ID_NULL_MSG = "Hotel ID cannot be null";

    /** Widest range a single calendar read may request, to bound response size. */
    private static final int MAX_RANGE_DAYS = 92;

    /** PostgreSQL SQLState for an EXCLUDE constraint violation. */
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    private final RateSeasonRepository rateSeasonRepository;
    private final RoomTypeService roomTypeService;
    private final RateSeasonMapper rateSeasonMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public RateCalendarResponse getCalendar(final UUID hotelId, final LocalDate startDate, final LocalDate endDate) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        validateRange(startDate, endDate);

        final List<RoomTypeResponse> roomTypes = roomTypeService.getAllRoomTypes(hotelId);
        final List<RateSeason> seasons =
                rateSeasonRepository.findAllByHotelIdAndDateRangeOverlapping(hotelId, startDate, endDate);
        final Map<UUID, List<RateSeason>> seasonsByRoomType = groupByRoomType(seasons);

        final List<RateCalendarRow> rows = new ArrayList<>();
        for (final RoomTypeResponse roomType : roomTypes) {
            final List<RateSeason> roomTypeSeasons = seasonsByRoomType.getOrDefault(roomType.id(), List.of());
            rows.add(buildRow(roomType, roomTypeSeasons, startDate, endDate));
        }
        return new RateCalendarResponse(rows);
    }

    private RateCalendarRow buildRow(
            final RoomTypeResponse roomType, final List<RateSeason> seasons,
            final LocalDate startDate, final LocalDate endDate) {
        final List<RateCalendarDay> days = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            days.add(resolveDay(roomType, seasons, date));
        }
        return new RateCalendarRow(roomType.id(), roomType.name(), roomType.basePrice(), days);
    }

    private RateCalendarDay resolveDay(
            final RoomTypeResponse roomType, final List<RateSeason> seasons, final LocalDate date) {
        for (final RateSeason season : seasons) {
            if (!date.isBefore(season.getStartDate()) && !date.isAfter(season.getEndDate())) {
                return new RateCalendarDay(date, season.getNightlyPrice(), season.getId(), season.getName());
            }
        }
        return new RateCalendarDay(date, roomType.basePrice(), null, null);
    }

    private static Map<UUID, List<RateSeason>> groupByRoomType(final List<RateSeason> seasons) {
        final Map<UUID, List<RateSeason>> byRoomType = new LinkedHashMap<>();
        for (final RateSeason season : seasons) {
            byRoomType.computeIfAbsent(season.getRoomTypeId(), key -> new ArrayList<>()).add(season);
        }
        return byRoomType;
    }

    private static void validateRange(final LocalDate startDate, final LocalDate endDate) {
        Objects.requireNonNull(startDate, "Start date cannot be null");
        Objects.requireNonNull(endDate, "End date cannot be null");
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("INVALID_DATE_RANGE");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_RANGE_DAYS) {
            throw new BadRequestException("DATE_RANGE_TOO_LARGE");
        }
    }

    // ------------------------------------------------------------------
    // Bulk apply
    // ------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    @Transactional
    public List<RateSeasonResponse> bulkApply(final UUID hotelId, final RateBulkApplyRequest request) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);

        final List<RateSeasonResponse> created = new ArrayList<>();
        for (final UUID roomTypeId : request.roomTypeIds()) {
            // Ownership check (T-ROOM-02) — a wrong-tenant roomTypeId must 404, not
            // silently apply to nothing.
            roomTypeService.getRoomTypeById(roomTypeId, hotelId);

            final List<RateSeason> overlapping = rateSeasonRepository.findOverlapping(
                    roomTypeId, hotelId, request.startDate(), request.endDate());
            for (final RateSeason existing : overlapping) {
                applySplitTrim(existing, request.startDate(), request.endDate());
            }
            // Flush before inserting the new season: the EXCLUDE constraint would
            // otherwise evaluate against the not-yet-flushed old boundaries of the
            // rows just trimmed/split above.
            rateSeasonRepository.flush();

            final RateSeason newSeason = RateSeason.builder()
                    .hotelId(hotelId)
                    .roomTypeId(roomTypeId)
                    .name(request.name())
                    .startDate(request.startDate())
                    .endDate(request.endDate())
                    .nightlyPrice(request.nightlyPrice())
                    .build();
            created.add(rateSeasonMapper.toResponse(saveTranslatingOverlap(newSeason)));
        }
        return created;
    }

    /**
     * Resolves one existing season against the newly-applied range, per the
     * four mutually-exclusive cases (overlap with the new range is guaranteed
     * by the caller, since {@code existing} came from
     * {@link RateSeasonRepository#findOverlapping}):
     * <ul>
     *   <li>fully covered by the new range → soft-deleted;</li>
     *   <li>pokes out only to the left ({@code existing.start < new.start}) →
     *       trimmed to end the day before the new range starts;</li>
     *   <li>pokes out only to the right ({@code existing.end > new.end}) →
     *       trimmed to start the day after the new range ends;</li>
     *   <li>pokes out on both sides (the new range is strictly interior) →
     *       split into two seasons, one on each side of the new range.</li>
     * </ul>
     *
     * @param existing the existing season, known to overlap {@code [newStart, newEnd]}
     * @param newStart the newly-applied range start (inclusive)
     * @param newEnd   the newly-applied range end (inclusive)
     */
    private void applySplitTrim(final RateSeason existing, final LocalDate newStart, final LocalDate newEnd) {
        final LocalDate start = existing.getStartDate();
        final LocalDate end = existing.getEndDate();
        final boolean pokesLeft = start.isBefore(newStart);
        final boolean pokesRight = end.isAfter(newEnd);

        if (!pokesLeft && !pokesRight) {
            rateSeasonRepository.delete(existing); // fully covered — @SQLDelete soft-deletes it
        } else if (pokesLeft && !pokesRight) {
            existing.setEndDate(newStart.minusDays(1));
            rateSeasonRepository.save(existing);
        } else if (!pokesLeft) {
            existing.setStartDate(newEnd.plusDays(1));
            rateSeasonRepository.save(existing);
        } else {
            splitAroundNewRange(existing, newStart, newEnd);
        }
    }

    private void splitAroundNewRange(final RateSeason existing, final LocalDate newStart, final LocalDate newEnd) {
        final LocalDate tailStart = newEnd.plusDays(1);
        final LocalDate tailEnd = existing.getEndDate();
        existing.setEndDate(newStart.minusDays(1));
        rateSeasonRepository.save(existing);
        rateSeasonRepository.save(RateSeason.builder()
                .hotelId(existing.getHotelId())
                .roomTypeId(existing.getRoomTypeId())
                .name(existing.getName())
                .startDate(tailStart)
                .endDate(tailEnd)
                .nightlyPrice(existing.getNightlyPrice())
                .build());
    }

    /**
     * Safety net mirroring {@code RateSeasonAdminServiceImpl.saveTranslatingOverlap}:
     * if the split/trim logic above ever leaves a gap the EXCLUDE constraint still
     * rejects, surface it as a clean 409 instead of a raw 500.
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
