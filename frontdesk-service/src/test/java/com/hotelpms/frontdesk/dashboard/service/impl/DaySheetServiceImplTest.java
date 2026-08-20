package com.hotelpms.frontdesk.dashboard.service.impl;

import com.hotelpms.frontdesk.dashboard.dto.DaySheetResponse;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.rooms.repository.RoomStatusCount;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DaySheetServiceImplTest {

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);
    private static final long GUESTS_IN_HOUSE = 5L;
    private static final int AVAILABLE_ROOMS = 7;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private StayRepository stayRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private DaySheetServiceImpl daySheetService;

    @BeforeEach
    void setUp() {
        when(reservationRepository.countByHotelIdAndCheckInDateAndStatusIn(eq(HOTEL_ID), eq(DATE), anySet()))
                .thenReturn(3);
        when(reservationRepository.countByHotelIdAndCheckOutDateAndStatusIn(eq(HOTEL_ID), eq(DATE), anySet()))
                .thenReturn(2);
        when(stayRepository.countByHotelIdAndStatus(HOTEL_ID, StayStatus.CHECKED_IN)).thenReturn(4L);
        when(stayRepository.countGuestsInHouseByHotelId(HOTEL_ID, StayStatus.CHECKED_IN))
                .thenReturn(GUESTS_IN_HOUSE);
        when(reservationService.getAvailableRooms(DATE, DATE.plusDays(1)))
                .thenReturn(Collections.nCopies(AVAILABLE_ROOMS, mock(RoomResponse.class)));
        final RoomStatusCount clean = mock(RoomStatusCount.class);
        when(clean.getStatus()).thenReturn(RoomStatus.CLEAN);
        when(clean.getCount()).thenReturn(10L);
        final RoomStatusCount dirty = mock(RoomStatusCount.class);
        when(dirty.getStatus()).thenReturn(RoomStatus.DIRTY);
        when(dirty.getCount()).thenReturn(2L);
        when(roomRepository.countRoomsByStatusForHotelId(HOTEL_ID)).thenReturn(List.of(clean, dirty));
    }

    @Test
    void shouldAggregateDaySheetFromAllFourRepositoriesScopedToHotel() {
        final DaySheetResponse response = daySheetService.getDaySheet(DATE, HOTEL_ID);

        assertEquals(DATE, response.date());
        assertEquals(3, response.todayArrivals());
        assertEquals(2, response.todayDepartures());
        assertEquals(GUESTS_IN_HOUSE, response.guestsInHouse());
        assertEquals(4L, response.currentStays());
        assertEquals(AVAILABLE_ROOMS, response.availableRooms());
        assertEquals(10L, response.roomStatusCounts().get(RoomStatus.CLEAN));
        assertEquals(2L, response.roomStatusCounts().get(RoomStatus.DIRTY));
    }

    @Test
    void shouldQueryArrivalsAndDeparturesWithDistinctStatusSets() {
        daySheetService.getDaySheet(DATE, HOTEL_ID);

        final ArgumentCaptor<Set<ReservationStatus>> arrivalStatuses = ArgumentCaptor.forClass(Set.class);
        verify(reservationRepository).countByHotelIdAndCheckInDateAndStatusIn(
                eq(HOTEL_ID), eq(DATE), arrivalStatuses.capture());
        assertEquals(Set.of(ReservationStatus.CONFIRMED, ReservationStatus.PENDING), arrivalStatuses.getValue());

        final ArgumentCaptor<Set<ReservationStatus>> departureStatuses = ArgumentCaptor.forClass(Set.class);
        verify(reservationRepository).countByHotelIdAndCheckOutDateAndStatusIn(
                eq(HOTEL_ID), eq(DATE), departureStatuses.capture());
        assertEquals(Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.PENDING),
                departureStatuses.getValue());
    }
}
