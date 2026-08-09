package com.hotelpms.frontdesk.pricing.service.impl;

import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import com.hotelpms.frontdesk.pricing.dto.RateBulkApplyRequest;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarDay;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarResponse;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;
import com.hotelpms.frontdesk.pricing.mapper.RateSeasonMapper;
import com.hotelpms.frontdesk.pricing.repository.RateSeasonRepository;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomTypeService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateCalendarServiceImplTest {

    private static final String PRICE_100 = "100.00";
    private static final String PRICE_150 = "150.00";
    private static final LocalDate JUL_10 = LocalDate.of(2026, 7, 10);
    private static final LocalDate JUL_20 = LocalDate.of(2026, 7, 20);
    private static final LocalDate AUG_1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate AUG_15 = LocalDate.of(2026, 8, 15);
    private static final LocalDate AUG_31 = LocalDate.of(2026, 8, 31);
    private static final int AUGUST_DAY_COUNT = 31;
    private static final int OVERSIZED_RANGE_DAYS = 200;

    @Mock
    private RateSeasonRepository rateSeasonRepository;

    @Mock
    private RoomTypeService roomTypeService;

    @Mock
    private RateSeasonMapper rateSeasonMapper;

    @InjectMocks
    private RateCalendarServiceImpl rateCalendarService;

    private UUID hotelId;
    private UUID roomTypeId;
    private RoomTypeResponse roomType;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();
        roomTypeId = UUID.randomUUID();
        roomType = new RoomTypeResponse(roomTypeId, "Double", "desc", 2,
                new BigDecimal(PRICE_100), true, null, null);
    }

    // ------------------------------------------------------------------
    // getCalendar
    // ------------------------------------------------------------------

    @Test
    void calendarResolvesBasePriceWhenNoSeasonCoversTheDate() {
        when(roomTypeService.getAllRoomTypes(hotelId)).thenReturn(List.of(roomType));
        when(rateSeasonRepository.findAllByHotelIdAndDateRangeOverlapping(hotelId, AUG_1, AUG_31))
                .thenReturn(List.of());

        final RateCalendarResponse result = rateCalendarService.getCalendar(hotelId, AUG_1, AUG_31);

        assertEquals(1, result.rows().size());
        assertEquals(AUGUST_DAY_COUNT, result.rows().get(0).days().size());
        result.rows().get(0).days().forEach(day -> {
            assertEquals(new BigDecimal(PRICE_100), day.price());
            assertNull(day.rateSeasonId());
        });
    }

    @Test
    void calendarResolvesSeasonPriceOnCoveredDaysAndBasePriceOnBoundary() {
        final UUID seasonId = UUID.randomUUID();
        final RateSeason season = RateSeason.builder()
                .id(seasonId).hotelId(hotelId).roomTypeId(roomTypeId).name("Alta")
                .startDate(AUG_1).endDate(AUG_15)
                .nightlyPrice(new BigDecimal(PRICE_150)).active(true).build();

        when(roomTypeService.getAllRoomTypes(hotelId)).thenReturn(List.of(roomType));
        when(rateSeasonRepository.findAllByHotelIdAndDateRangeOverlapping(hotelId, AUG_1, AUG_31))
                .thenReturn(List.of(season));

        final RateCalendarResponse result = rateCalendarService.getCalendar(hotelId, AUG_1, AUG_31);

        final List<RateCalendarDay> days = result.rows().get(0).days();
        final RateCalendarDay lastCoveredDay = days.get(14); // Aug 15 — the season's last day (inclusive)
        final RateCalendarDay firstUncoveredDay = days.get(15); // Aug 16 — first day after the season
        assertEquals(new BigDecimal(PRICE_150), lastCoveredDay.price());
        assertEquals(seasonId, lastCoveredDay.rateSeasonId());
        assertEquals(new BigDecimal(PRICE_100), firstUncoveredDay.price());
        assertNull(firstUncoveredDay.rateSeasonId());
    }

    @Test
    void calendarWithEndBeforeStartThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> rateCalendarService.getCalendar(hotelId, AUG_31, AUG_1));
    }

    @Test
    void calendarWithRangeTooLargeThrowsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> rateCalendarService.getCalendar(hotelId, AUG_1, AUG_1.plusDays(OVERSIZED_RANGE_DAYS)));
    }

    // ------------------------------------------------------------------
    // bulkApply — split/trim cases
    // ------------------------------------------------------------------

    @Test
    void bulkApplyWithNoExistingOverlapJustCreatesTheNewSeason() {
        stubNewSeasonCreation();
        when(rateSeasonRepository.findOverlapping(roomTypeId, hotelId, AUG_1, AUG_31)).thenReturn(List.of());

        final List<RateSeasonResponse> result = rateCalendarService.bulkApply(hotelId, bulkRequest(AUG_1, AUG_31));

        assertEquals(1, result.size());
        verify(rateSeasonRepository, times(0)).delete(any());
    }

    @Test
    void bulkApplyFullyCoveringAnExistingSeasonSoftDeletesIt() {
        stubNewSeasonCreation();
        final RateSeason existing = seasonFor(AUG_1, AUG_31);
        when(rateSeasonRepository.findOverlapping(roomTypeId, hotelId, AUG_1, AUG_31)).thenReturn(List.of(existing));

        rateCalendarService.bulkApply(hotelId, bulkRequest(AUG_1, AUG_31));

        verify(rateSeasonRepository).delete(existing);
    }

    @Test
    void bulkApplyPokingOnlyLeftTrimsTheExistingSeasonEnd() {
        stubNewSeasonCreation();
        // existing: Jul 20 - Aug 10; new range: Aug 1 - Aug 31 -> existing pokes out to the left only
        final LocalDate existingEnd = LocalDate.of(2026, 8, 10);
        final RateSeason existing = seasonFor(JUL_20, existingEnd);
        when(rateSeasonRepository.findOverlapping(roomTypeId, hotelId, AUG_1, AUG_31)).thenReturn(List.of(existing));

        rateCalendarService.bulkApply(hotelId, bulkRequest(AUG_1, AUG_31));

        assertEquals(JUL_20, existing.getStartDate());
        assertEquals(AUG_1.minusDays(1), existing.getEndDate());
        verify(rateSeasonRepository, times(0)).delete(existing);
    }

    @Test
    void bulkApplyPokingOnlyRightTrimsTheExistingSeasonStart() {
        stubNewSeasonCreation();
        // existing: Aug 20 - Sep 10; new range: Aug 1 - Aug 31 -> existing pokes out to the right only
        final LocalDate existingStart = LocalDate.of(2026, 8, 20);
        final LocalDate existingEnd = LocalDate.of(2026, 9, 10);
        final RateSeason existing = seasonFor(existingStart, existingEnd);
        when(rateSeasonRepository.findOverlapping(roomTypeId, hotelId, AUG_1, AUG_31)).thenReturn(List.of(existing));

        rateCalendarService.bulkApply(hotelId, bulkRequest(AUG_1, AUG_31));

        assertEquals(AUG_31.plusDays(1), existing.getStartDate());
        assertEquals(existingEnd, existing.getEndDate());
    }

    @Test
    void bulkApplyStrictlyInsideAnExistingSeasonSplitsItInTwo() {
        stubNewSeasonCreation();
        // existing: Jul 1 - Sep 30; new range: Aug 1 - Aug 31, strictly interior -> split
        final LocalDate existingStart = LocalDate.of(2026, 7, 1);
        final LocalDate existingEnd = LocalDate.of(2026, 9, 30);
        final RateSeason existing = seasonFor(existingStart, existingEnd);
        when(rateSeasonRepository.findOverlapping(roomTypeId, hotelId, AUG_1, AUG_31)).thenReturn(List.of(existing));

        rateCalendarService.bulkApply(hotelId, bulkRequest(AUG_1, AUG_31));

        assertEquals(existingStart, existing.getStartDate());
        assertEquals(AUG_1.minusDays(1), existing.getEndDate());

        final ArgumentCaptor<RateSeason> savedCaptor = ArgumentCaptor.forClass(RateSeason.class);
        verify(rateSeasonRepository, times(2)).save(savedCaptor.capture());
        final RateSeason tail = savedCaptor.getAllValues().get(1);
        assertEquals(AUG_31.plusDays(1), tail.getStartDate());
        assertEquals(existingEnd, tail.getEndDate());
        assertEquals(existing.getNightlyPrice(), tail.getNightlyPrice());
    }

    @Test
    void bulkApplyFlushesBeforeInsertingTheNewSeason() {
        stubNewSeasonCreation();
        when(rateSeasonRepository.findOverlapping(roomTypeId, hotelId, AUG_1, AUG_31)).thenReturn(List.of());

        rateCalendarService.bulkApply(hotelId, bulkRequest(AUG_1, AUG_31));

        verify(rateSeasonRepository).flush();
    }

    @Test
    void bulkApplyTranslatesResidualOverlapIntoConflict() {
        when(rateSeasonRepository.findOverlapping(roomTypeId, hotelId, AUG_1, AUG_31)).thenReturn(List.of());
        when(rateSeasonRepository.saveAndFlush(any(RateSeason.class))).thenThrow(exclusionViolation());

        assertThrows(ConflictException.class,
                () -> rateCalendarService.bulkApply(hotelId, bulkRequest(AUG_1, AUG_31)));
    }

    private void stubNewSeasonCreation() {
        when(rateSeasonRepository.saveAndFlush(any(RateSeason.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(rateSeasonMapper.toResponse(any(RateSeason.class)))
                .thenAnswer(invocation -> {
                    final RateSeason s = invocation.getArgument(0);
                    return new RateSeasonResponse(s.getId(), s.getRoomTypeId(), s.getName(),
                            s.getStartDate(), s.getEndDate(), s.getNightlyPrice());
                });
    }

    private RateSeason seasonFor(final LocalDate start, final LocalDate end) {
        return RateSeason.builder()
                .id(UUID.randomUUID()).hotelId(hotelId).roomTypeId(roomTypeId).name("Existing")
                .startDate(start).endDate(end).nightlyPrice(new BigDecimal(PRICE_100)).active(true).build();
    }

    private RateBulkApplyRequest bulkRequest(final LocalDate start, final LocalDate end) {
        return new RateBulkApplyRequest(List.of(roomTypeId), start, end, new BigDecimal(PRICE_150), "Alta stagione");
    }

    private static DataIntegrityViolationException exclusionViolation() {
        final SQLException sqlException = new SQLException("overlap", "23P01");
        return new DataIntegrityViolationException("excl_rate_seasons_no_overlap", sqlException);
    }
}
