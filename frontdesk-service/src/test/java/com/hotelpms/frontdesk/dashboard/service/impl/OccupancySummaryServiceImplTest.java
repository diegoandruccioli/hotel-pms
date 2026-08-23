package com.hotelpms.frontdesk.dashboard.service.impl;

import com.hotelpms.frontdesk.dashboard.dto.OccupancySummaryResponse;
import com.hotelpms.frontdesk.dashboard.dto.ReportGranularity;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.stays.repository.StayOccupancyPeriod;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OccupancySummaryServiceImplTest {

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate DATE_FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 9, 1);
    private static final LocalDate WEEK1_START = LocalDate.of(2026, 8, 3);
    private static final LocalDate WEEK2_START = LocalDate.of(2026, 8, 10);
    private static final long WEEK1_NIGHTS = 20L;
    private static final long WEEK2_NIGHTS = 35L;
    private static final long TOTAL_ROOMS = 12L;
    private static final long OTHER_TOTAL_ROOMS = 5L;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private StayRepository stayRepository;

    @InjectMocks
    private OccupancySummaryServiceImpl occupancySummaryService;

    @Test
    void aggregatesRoomCountAndOccupiedNightsPerPeriod() {
        when(roomRepository.countByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(TOTAL_ROOMS);

        final StayOccupancyPeriod week1 = mock(StayOccupancyPeriod.class);
        when(week1.getPeriodStart()).thenReturn(WEEK1_START);
        when(week1.getOccupiedRoomNights()).thenReturn(WEEK1_NIGHTS);
        final StayOccupancyPeriod week2 = mock(StayOccupancyPeriod.class);
        when(week2.getPeriodStart()).thenReturn(WEEK2_START);
        when(week2.getOccupiedRoomNights()).thenReturn(WEEK2_NIGHTS);

        when(stayRepository.sumOccupiedRoomNightsByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(DATE_FROM.atStartOfDay()), eq(DATE_TO.atStartOfDay()), eq("week")))
                .thenReturn(List.of(week1, week2));

        final OccupancySummaryResponse response = occupancySummaryService.getOccupancySummary(
                DATE_FROM, DATE_TO, ReportGranularity.WEEK, HOTEL_ID);

        assertEquals((int) TOTAL_ROOMS, response.totalRooms());
        assertEquals(2, response.periods().size());
        assertEquals(WEEK1_START, response.periods().get(0).periodStart());
        assertEquals(WEEK1_NIGHTS, response.periods().get(0).occupiedRoomNights());
        assertEquals(WEEK2_START, response.periods().get(1).periodStart());
        assertEquals(WEEK2_NIGHTS, response.periods().get(1).occupiedRoomNights());
    }

    @Test
    void passesTheValidatedGranularitysLowercaseSqlValueToTheQuery() {
        when(roomRepository.countByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(0L);
        when(stayRepository.sumOccupiedRoomNightsByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), any(LocalDateTime.class), any(LocalDateTime.class), eq("month")))
                .thenReturn(List.of());

        occupancySummaryService.getOccupancySummary(DATE_FROM, DATE_TO, ReportGranularity.MONTH, HOTEL_ID);

        verify(stayRepository).sumOccupiedRoomNightsByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(DATE_FROM.atStartOfDay()), eq(DATE_TO.atStartOfDay()), eq("month"));
    }

    @Test
    void returnsEmptyPeriodsWhenNoStaysCheckedOutInWindow() {
        when(roomRepository.countByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(OTHER_TOTAL_ROOMS);
        when(stayRepository.sumOccupiedRoomNightsByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(DATE_FROM.atStartOfDay()), eq(DATE_TO.atStartOfDay()), eq("day")))
                .thenReturn(List.of());

        final OccupancySummaryResponse response = occupancySummaryService.getOccupancySummary(
                DATE_FROM, DATE_TO, ReportGranularity.DAY, HOTEL_ID);

        assertEquals((int) OTHER_TOTAL_ROOMS, response.totalRooms());
        assertEquals(List.of(), response.periods());
    }
}
