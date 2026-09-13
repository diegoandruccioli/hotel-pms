package com.hotelpms.frontdesk.housekeeping.service.impl;

import com.hotelpms.frontdesk.housekeeping.domain.HousekeepingTaskType;
import com.hotelpms.frontdesk.housekeeping.dto.HousekeepingRow;
import com.hotelpms.frontdesk.housekeeping.dto.HousekeepingWorksheetResponse;
import com.hotelpms.frontdesk.housekeeping.service.BusinessDateResolver;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditRun;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditStatus;
import com.hotelpms.frontdesk.nightaudit.repository.NightAuditRunRepository;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationLineItem;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.rooms.domain.Room;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.domain.RoomType;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import com.hotelpms.pdftemplate.PdfTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HousekeepingWorksheetServiceImpl} — the room-by-room
 * classification logic behind the housekeeping worksheet.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class HousekeepingWorksheetServiceImplTest {

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);
    private static final UUID GUEST_ID = UUID.randomUUID();

    @Mock
    private StayRepository stayRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private NightAuditRunRepository nightAuditRunRepository;

    @Mock
    private HotelSettingsService hotelSettingsService;

    @Mock
    private BusinessDateResolver businessDateResolver;

    @Mock
    private PdfTemplateRenderer pdfTemplateRenderer;

    @InjectMocks
    private HousekeepingWorksheetServiceImpl housekeepingWorksheetService;

    @BeforeEach
    void stubHotelName() {
        when(hotelSettingsService.getOrCreate(HOTEL_ID)).thenReturn(new HotelSettingsResponse(
                HOTEL_ID, false, "Test Hotel", null, null, null, null, null, false,
                true, true, null, null, null, null, null, null, null, "Europe/Rome", 4));
        lenient().when(reservationRepository.findByHotelIdAndCheckInDateAndStatusIn(eq(HOTEL_ID), eq(DATE), any()))
                .thenReturn(List.of());
        lenient().when(stayRepository.findByHotelIdAndStatusWithGuests(HOTEL_ID, StayStatus.CHECKED_IN))
                .thenReturn(List.of());
        when(roomRepository.findAllByActiveTrueAndHotelIdAndStatus(HOTEL_ID, RoomStatus.DIRTY)).thenReturn(List.of());
        when(roomRepository.findAllByActiveTrueAndHotelIdAndStatus(HOTEL_ID, RoomStatus.MAINTENANCE)).thenReturn(List.of());
    }

    @Test
    void classifiesADepartureDueTodayAsDeparture() {
        final Room room = room("101", RoomStatus.OCCUPIED);
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of(room));
        when(stayRepository.findByHotelIdAndStatusWithGuests(HOTEL_ID, StayStatus.CHECKED_IN))
                .thenReturn(List.of(stay(room, DATE, 2)));

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertEquals(1, worksheet.rows().size());
        final HousekeepingRow row = worksheet.rows().get(0);
        assertEquals(HousekeepingTaskType.DEPARTURE, row.taskType());
        assertEquals("101", row.roomNumber());
        assertEquals(2, row.pax());
        assertFalse(row.turnover());
    }

    @Test
    void classifiesAnOverdueDepartureAsDepartureNotStayover() {
        // expectedCheckOutDate is in the past relative to the worksheet date —
        // must still surface as a DEPARTURE, never silently vanish.
        final Room room = room("102", RoomStatus.OCCUPIED);
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of(room));
        when(stayRepository.findByHotelIdAndStatusWithGuests(HOTEL_ID, StayStatus.CHECKED_IN))
                .thenReturn(List.of(stay(room, DATE.minusDays(2), 1)));

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertEquals(1, worksheet.rows().size());
        assertEquals(HousekeepingTaskType.DEPARTURE, worksheet.rows().get(0).taskType());
    }

    @Test
    void classifiesAFutureCheckOutAsStayover() {
        final Room room = room("103", RoomStatus.OCCUPIED);
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of(room));
        when(stayRepository.findByHotelIdAndStatusWithGuests(HOTEL_ID, StayStatus.CHECKED_IN))
                .thenReturn(List.of(stay(room, DATE.plusDays(3), 1)));

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertEquals(1, worksheet.rows().size());
        final HousekeepingRow row = worksheet.rows().get(0);
        assertEquals(HousekeepingTaskType.STAYOVER, row.taskType());
        assertFalse(row.departureDateUnknown());
    }

    @Test
    void nullExpectedCheckOutDateIsConservativelyStayoverNotDropped() {
        final Room room = room("104", RoomStatus.OCCUPIED);
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of(room));
        when(stayRepository.findByHotelIdAndStatusWithGuests(HOTEL_ID, StayStatus.CHECKED_IN))
                .thenReturn(List.of(stay(room, null, 1)));

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertEquals(1, worksheet.rows().size());
        final HousekeepingRow row = worksheet.rows().get(0);
        assertEquals(HousekeepingTaskType.STAYOVER, row.taskType());
        assertTrue(row.departureDateUnknown());
    }

    @Test
    void sameDayArrivalIntoADepartingRoomIsFoldedIntoTurnoverNotADuplicateRow() {
        final Room room = room("105", RoomStatus.OCCUPIED);
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of(room));
        when(stayRepository.findByHotelIdAndStatusWithGuests(HOTEL_ID, StayStatus.CHECKED_IN))
                .thenReturn(List.of(stay(room, DATE, 1)));
        when(reservationRepository.findByHotelIdAndCheckInDateAndStatusIn(
                eq(HOTEL_ID), eq(DATE), any())).thenReturn(List.of(reservation(room, 2)));

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertEquals(1, worksheet.rows().size());
        final HousekeepingRow row = worksheet.rows().get(0);
        assertEquals(HousekeepingTaskType.DEPARTURE, row.taskType());
        assertTrue(row.turnover());
    }

    @Test
    void arrivalIntoAnUnrelatedRoomIsItsOwnArrivalPrepRow() {
        final Room arrivingRoom = room("106", RoomStatus.CLEAN);
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of(arrivingRoom));
        when(reservationRepository.findByHotelIdAndCheckInDateAndStatusIn(
                eq(HOTEL_ID), eq(DATE), any())).thenReturn(List.of(reservation(arrivingRoom, 3)));

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertEquals(1, worksheet.rows().size());
        final HousekeepingRow row = worksheet.rows().get(0);
        assertEquals(HousekeepingTaskType.ARRIVAL_PREP, row.taskType());
        assertEquals(3, row.pax());
    }

    @Test
    void vacantDirtyRoomIsListedWithZeroPax() {
        final Room dirtyRoom = room("107", RoomStatus.DIRTY);
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of(dirtyRoom));
        when(roomRepository.findAllByActiveTrueAndHotelIdAndStatus(HOTEL_ID, RoomStatus.DIRTY))
                .thenReturn(List.of(dirtyRoom));

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertEquals(1, worksheet.rows().size());
        final HousekeepingRow row = worksheet.rows().get(0);
        assertEquals(HousekeepingTaskType.VACANT_DIRTY, row.taskType());
        assertEquals(0, row.pax());
    }

    @Test
    void maintenanceRoomAlreadyCoveredAsDepartureIsNotDuplicated() {
        // Defensive dedup: can't happen with real data (a room can't be both
        // OCCUPIED-with-a-checked-in-stay and MAINTENANCE), but proves the
        // guard filters it out if the invariant were ever violated.
        final Room room = room("108", RoomStatus.OCCUPIED);
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of(room));
        when(stayRepository.findByHotelIdAndStatusWithGuests(HOTEL_ID, StayStatus.CHECKED_IN))
                .thenReturn(List.of(stay(room, DATE, 1)));
        when(roomRepository.findAllByActiveTrueAndHotelIdAndStatus(HOTEL_ID, RoomStatus.MAINTENANCE))
                .thenReturn(List.of(room));

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertEquals(1, worksheet.rows().size());
        assertEquals(HousekeepingTaskType.DEPARTURE, worksheet.rows().get(0).taskType());
    }

    @Test
    void provisionalWhenNoCompletedNightAuditForPriorDay() {
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of());
        when(nightAuditRunRepository.findByHotelIdAndBusinessDate(HOTEL_ID, DATE.minusDays(1)))
                .thenReturn(Optional.empty());

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertTrue(worksheet.provisional());
    }

    @Test
    void definitiveWhenPriorDayNightAuditCompleted() {
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of());
        final NightAuditRun completedRun = NightAuditRun.builder()
                .hotelId(HOTEL_ID).businessDate(DATE.minusDays(1)).status(NightAuditStatus.COMPLETED).build();
        when(nightAuditRunRepository.findByHotelIdAndBusinessDate(HOTEL_ID, DATE.minusDays(1)))
                .thenReturn(Optional.of(completedRun));

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, DATE);

        assertFalse(worksheet.provisional());
    }

    @Test
    void nullDateResolvesViaBusinessDateResolver() {
        when(businessDateResolver.resolve(HOTEL_ID)).thenReturn(DATE);
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of());

        final HousekeepingWorksheetResponse worksheet = housekeepingWorksheetService.getWorksheet(HOTEL_ID, null);

        assertEquals(DATE, worksheet.date());
    }

    @Test
    void pdfRendersUsingTheSameWorksheetData() {
        when(roomRepository.findAllByActiveTrueAndHotelId(HOTEL_ID)).thenReturn(List.of());
        when(pdfTemplateRenderer.render(eq("housekeeping-worksheet"), any())).thenReturn(new byte[] {1, 2, 3});

        final byte[] pdf = housekeepingWorksheetService.getWorksheetPdf(HOTEL_ID, DATE);

        assertEquals(3, pdf.length);
    }

    private static Room room(final String roomNumber, final RoomStatus status) {
        return Room.builder()
                .id(UUID.randomUUID())
                .hotelId(HOTEL_ID)
                .roomNumber(roomNumber)
                .roomType(RoomType.builder().name("Standard").build())
                .status(status)
                .active(true)
                .build();
    }

    private static Stay stay(final Room room, final LocalDate expectedCheckOutDate, final int pax) {
        return Stay.builder()
                .id(UUID.randomUUID())
                .hotelId(HOTEL_ID)
                .roomId(room.getId())
                .guestId(GUEST_ID)
                .status(StayStatus.CHECKED_IN)
                .expectedCheckOutDate(expectedCheckOutDate)
                .guests(guests(pax))
                .build();
    }

    private static List<StayGuest> guests(final int pax) {
        return java.util.stream.IntStream.range(0, pax)
                .mapToObj(i -> StayGuest.builder().id(UUID.randomUUID()).build())
                .toList();
    }

    private static Reservation reservation(final Room room, final int expectedGuests) {
        final Reservation reservation = Reservation.builder()
                .id(UUID.randomUUID())
                .hotelId(HOTEL_ID)
                .guestId(GUEST_ID)
                .expectedGuests(expectedGuests)
                .checkInDate(DATE)
                .checkOutDate(DATE.plusDays(1))
                .status(ReservationStatus.CONFIRMED)
                .build();
        reservation.setLineItems(List.of(ReservationLineItem.builder()
                .reservation(reservation)
                .roomId(room.getId())
                .active(true)
                .build()));
        return reservation;
    }
}
