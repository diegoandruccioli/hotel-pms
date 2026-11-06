package com.hotelpms.frontdesk.nightaudit.service.impl;

import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditRun;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditStatus;
import com.hotelpms.frontdesk.nightaudit.dto.DaySheetTrendPoint;
import com.hotelpms.frontdesk.nightaudit.dto.DaySheetTrendResponse;
import com.hotelpms.frontdesk.nightaudit.repository.NightAuditRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DaySheetTrendServiceImplTest {

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 14;
    private static final long GUESTS_IN_HOUSE = 21L;
    private static final int ARRIVALS = 5;
    private static final int DEPARTURES = 4;
    private static final int AVAILABLE_ROOMS = 9;
    private static final int NEGATIVE_DAYS = -3;
    private static final String OUT_OF_RANGE = "DAY_SHEET_TREND_DAYS_OUT_OF_RANGE";

    @Mock
    private NightAuditRunRepository nightAuditRunRepository;

    @InjectMocks
    private DaySheetTrendServiceImpl service;

    private static NightAuditRun run(final LocalDate businessDate) {
        return NightAuditRun.builder()
                .hotelId(HOTEL_ID)
                .businessDate(businessDate)
                .status(NightAuditStatus.COMPLETED)
                .arrivals(ARRIVALS)
                .departures(DEPARTURES)
                .guestsInHouse(GUESTS_IN_HOUSE)
                .availableRooms(AVAILABLE_ROOMS)
                .build();
    }

    @Test
    void queriesTheWindowBeforeTheReferenceDateForCompletedRunsOfTheCallersHotel() {
        when(nightAuditRunRepository.findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc(
                HOTEL_ID, NightAuditStatus.COMPLETED, DATE.minusDays(DEFAULT_DAYS), DATE.minusDays(1)))
                .thenReturn(List.of());

        final DaySheetTrendResponse response = service.getTrend(DATE, DEFAULT_DAYS, HOTEL_ID);

        assertEquals(DATE.minusDays(DEFAULT_DAYS), response.from());
        assertEquals(DATE.minusDays(1), response.to());
        assertTrue(response.points().isEmpty());
    }

    @Test
    void mapsEachRunToAPointKeepingTheRepositoryOrder() {
        final LocalDate first = DATE.minusDays(2);
        final LocalDate second = DATE.minusDays(1);
        when(nightAuditRunRepository.findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc(
                HOTEL_ID, NightAuditStatus.COMPLETED, DATE.minusDays(2), DATE.minusDays(1)))
                .thenReturn(List.of(run(first), run(second)));

        final List<DaySheetTrendPoint> points = service.getTrend(DATE, 2, HOTEL_ID).points();

        assertEquals(List.of(
                new DaySheetTrendPoint(first, ARRIVALS, DEPARTURES, GUESTS_IN_HOUSE, AVAILABLE_ROOMS),
                new DaySheetTrendPoint(second, ARRIVALS, DEPARTURES, GUESTS_IN_HOUSE, AVAILABLE_ROOMS)), points);
    }

    @Test
    void dropsACompletedRunWhoseSnapshotWasNeverFilled() {
        final NightAuditRun incomplete = run(DATE.minusDays(1));
        incomplete.setGuestsInHouse(null);
        when(nightAuditRunRepository.findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc(
                HOTEL_ID, NightAuditStatus.COMPLETED, DATE.minusDays(1), DATE.minusDays(1)))
                .thenReturn(List.of(incomplete));

        assertTrue(service.getTrend(DATE, 1, HOTEL_ID).points().isEmpty());
    }

    @Test
    void dropsARunMissingAnyOtherSnapshotColumn() {
        final NightAuditRun noArrivals = run(DATE.minusDays(3));
        noArrivals.setArrivals(null);
        final NightAuditRun noDepartures = run(DATE.minusDays(2));
        noDepartures.setDepartures(null);
        final NightAuditRun noRooms = run(DATE.minusDays(1));
        noRooms.setAvailableRooms(null);
        when(nightAuditRunRepository.findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc(
                HOTEL_ID, NightAuditStatus.COMPLETED, DATE.minusDays(3), DATE.minusDays(1)))
                .thenReturn(List.of(noArrivals, noDepartures, noRooms));

        assertTrue(service.getTrend(DATE, 3, HOTEL_ID).points().isEmpty());
    }

    @Test
    void acceptsTheLimitsOfTheAllowedWindow() {
        when(nightAuditRunRepository.findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc(
                HOTEL_ID, NightAuditStatus.COMPLETED, DATE.minusDays(MAX_DAYS), DATE.minusDays(1)))
                .thenReturn(List.of());
        when(nightAuditRunRepository.findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc(
                HOTEL_ID, NightAuditStatus.COMPLETED, DATE.minusDays(1), DATE.minusDays(1)))
                .thenReturn(List.of());

        assertEquals(DATE.minusDays(MAX_DAYS), service.getTrend(DATE, MAX_DAYS, HOTEL_ID).from());
        assertEquals(DATE.minusDays(1), service.getTrend(DATE, 1, HOTEL_ID).from());
        verify(nightAuditRunRepository).findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc(
                HOTEL_ID, NightAuditStatus.COMPLETED, DATE.minusDays(MAX_DAYS), DATE.minusDays(1));
    }

    @Test
    void rejectsADaysValueOutsideOneToFourteenWithoutTouchingTheDatabase() {
        final BadRequestException tooFew = assertThrows(BadRequestException.class,
                () -> service.getTrend(DATE, 0, HOTEL_ID));
        final BadRequestException negative = assertThrows(BadRequestException.class,
                () -> service.getTrend(DATE, NEGATIVE_DAYS, HOTEL_ID));
        final BadRequestException tooMany = assertThrows(BadRequestException.class,
                () -> service.getTrend(DATE, MAX_DAYS + 1, HOTEL_ID));

        assertEquals(OUT_OF_RANGE, tooFew.getMessage());
        assertEquals(OUT_OF_RANGE, negative.getMessage());
        assertEquals(OUT_OF_RANGE, tooMany.getMessage());
        verifyNoInteractions(nightAuditRunRepository);
    }
}
