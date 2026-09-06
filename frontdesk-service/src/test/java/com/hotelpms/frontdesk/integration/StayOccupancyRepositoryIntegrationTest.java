package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.rooms.domain.Room;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.domain.RoomType;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.rooms.repository.RoomTypeRepository;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.repository.StayOccupancyPeriod;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for {@link StayRepository#sumOccupiedRoomNightsByHotelIdGroupedByPeriod}
 * against a real PostgreSQL database — the {@code generate_series}-based native query cannot
 * be exercised by a mocked-repository unit test (see {@code OccupancySummaryServiceImplTest},
 * which mocks {@link StayRepository} entirely).
 *
 * <p>Rewritten to count night-by-night rather than summing a stay's whole length into its
 * arrival-date bucket, and to include {@code CHECKED_IN} (still in-house) stays, not only
 * {@code CHECKED_OUT} ones — a report window covering mostly still-in-progress stays (the
 * common "this week" case) used to report zero occupied nights next to genuine non-zero
 * revenue for the same period (found in live QA, 2026-09).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true"
})
class StayOccupancyRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final String GRANULARITY_DAY = "day";
    private static final BigDecimal BASE_PRICE = new BigDecimal("90.00");
    private static final int MAX_OCCUPANCY = 2;
    private static final LocalTime AFTERNOON_CHECK_IN = LocalTime.of(14, 0);
    private static final LocalTime MORNING_CHECK_OUT = LocalTime.of(10, 0);
    private static final long THREE_NIGHTS = 3L;

    // countsEveryNightOfAStillCheckedInStayThatFallsWithinTheWindow
    private static final LocalDate JAN_CHECK_IN = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_EXPECTED_CHECK_OUT = LocalDate.of(2026, 1, 5);
    private static final LocalDate JAN_WINDOW_END = LocalDate.of(2026, 1, 4);

    // countsACheckedOutStaysNightsButExcludesTheDepartureNightItself
    private static final LocalDate FEB_CHECK_IN = LocalDate.of(2026, 2, 1);
    private static final LocalDate FEB_CHECK_OUT = LocalDate.of(2026, 2, 4);
    private static final LocalDate FEB_WINDOW_END = LocalDate.of(2026, 2, 6);

    // aStayThatArrivedBeforeTheWindowStillCountsItsNightsInsideTheWindow
    private static final LocalDate MAR_CHECK_IN = LocalDate.of(2026, 3, 28);
    private static final LocalDate APR_EXPECTED_CHECK_OUT = LocalDate.of(2026, 4, 10);
    private static final LocalDate APR_WINDOW_START = LocalDate.of(2026, 4, 1);
    private static final LocalDate APR_WINDOW_END = LocalDate.of(2026, 4, 4);

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private StayRepository stayRepository;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void countsEveryNightOfAStillCheckedInStayThatFallsWithinTheWindow() {
        // Checked in Jan 1, expected out Jan 5 (4 nights), still in-house — no
        // actualCheckOutTime. A query window ending Jan 4 (exclusive) must count
        // exactly 3 occupied nights (Jan 1, 2, 3), even though the stay itself
        // continues past the window and was never CHECKED_OUT — the old query
        // counted this stay as 0 nights everywhere, since it only summed
        // CHECKED_OUT stays.
        final LocalDate checkIn = JAN_CHECK_IN;
        final LocalDate expectedCheckOut = JAN_EXPECTED_CHECK_OUT;
        final LocalDate windowEnd = JAN_WINDOW_END;
        final UUID hotelId = UUID.randomUUID();
        final UUID roomId = seedRoom(hotelId);
        seedStay(hotelId, roomId, StayStatus.CHECKED_IN,
                checkIn.atTime(AFTERNOON_CHECK_IN), null, expectedCheckOut);

        final List<StayOccupancyPeriod> periods = stayRepository.sumOccupiedRoomNightsByHotelIdGroupedByPeriod(
                hotelId, checkIn.atStartOfDay(), windowEnd.atStartOfDay(), GRANULARITY_DAY);

        assertEquals(THREE_NIGHTS, totalNights(periods), "expected 3 occupied nights (Jan 1-3), got: " + periods);
        assertEquals(3, periods.size(), "expected one non-empty day bucket per occupied night, got: " + periods);
    }

    @Test
    void countsACheckedOutStaysNightsButExcludesTheDepartureNightItself() {
        // Checked in Feb 1 14:00, checked out Feb 4 10:00 — 3 nights occupied
        // (Feb 1, 2, 3); Feb 4 is the departure day, not an occupied night.
        final LocalDate checkIn = FEB_CHECK_IN;
        final LocalDate checkOut = FEB_CHECK_OUT;
        final LocalDate windowEnd = FEB_WINDOW_END;
        final UUID hotelId = UUID.randomUUID();
        final UUID roomId = seedRoom(hotelId);
        seedStay(hotelId, roomId, StayStatus.CHECKED_OUT,
                checkIn.atTime(AFTERNOON_CHECK_IN), checkOut.atTime(MORNING_CHECK_OUT), checkOut);

        final List<StayOccupancyPeriod> periods = stayRepository.sumOccupiedRoomNightsByHotelIdGroupedByPeriod(
                hotelId, checkIn.atStartOfDay(), windowEnd.atStartOfDay(), GRANULARITY_DAY);

        assertEquals(THREE_NIGHTS, totalNights(periods),
                "expected 3 occupied nights (Feb 1-3, departure night excluded), got: " + periods);
    }

    @Test
    void aStayThatArrivedBeforeTheWindowStillCountsItsNightsInsideTheWindow() {
        // Arrived Mar 28 (before the query window starts Apr 1), still checked in.
        // The old arrival-date-bucketed query would have missed this stay entirely
        // (its arrival date falls outside [start, end)) even though it occupies a
        // room for every night of the window.
        final LocalDate checkIn = MAR_CHECK_IN;
        final LocalDate expectedCheckOut = APR_EXPECTED_CHECK_OUT;
        final LocalDate windowStart = APR_WINDOW_START;
        final LocalDate windowEnd = APR_WINDOW_END;
        final UUID hotelId = UUID.randomUUID();
        final UUID roomId = seedRoom(hotelId);
        seedStay(hotelId, roomId, StayStatus.CHECKED_IN,
                checkIn.atTime(AFTERNOON_CHECK_IN), null, expectedCheckOut);

        final List<StayOccupancyPeriod> periods = stayRepository.sumOccupiedRoomNightsByHotelIdGroupedByPeriod(
                hotelId, windowStart.atStartOfDay(), windowEnd.atStartOfDay(), GRANULARITY_DAY);

        assertEquals(THREE_NIGHTS, totalNights(periods), "expected 3 occupied nights (Apr 1-3), got: " + periods);
    }

    private static long totalNights(final List<StayOccupancyPeriod> periods) {
        return periods.stream().mapToLong(StayOccupancyPeriod::getOccupiedRoomNights).sum();
    }

    private UUID seedRoom(final UUID hotelId) {
        final RoomType roomType = roomTypeRepository.saveAndFlush(RoomType.builder()
                .hotelId(hotelId)
                .name("Standard-" + UUID.randomUUID())
                .maxOccupancy(MAX_OCCUPANCY)
                .basePrice(BASE_PRICE)
                .build());
        final Room room = roomRepository.saveAndFlush(Room.builder()
                .hotelId(hotelId)
                .roomNumber("R-" + UUID.randomUUID().toString().substring(0, 8))
                .roomType(roomType)
                .status(RoomStatus.OCCUPIED)
                .build());
        return room.getId();
    }

    private void seedStay(final UUID hotelId, final UUID roomId, final StayStatus status,
            final LocalDateTime actualCheckInTime, final LocalDateTime actualCheckOutTime,
            final LocalDate expectedCheckOutDate) {
        final UUID guestId = UUID.randomUUID();
        final Reservation reservation = reservationRepository.saveAndFlush(Reservation.builder()
                .hotelId(hotelId)
                .guestId(guestId)
                .expectedGuests(1)
                .checkInDate(actualCheckInTime.toLocalDate())
                .checkOutDate(expectedCheckOutDate)
                .status(ReservationStatus.CHECKED_IN)
                .build());

        stayRepository.saveAndFlush(Stay.builder()
                .hotelId(hotelId)
                .reservationId(reservation.getId())
                .guestId(guestId)
                .roomId(roomId)
                .status(status)
                .actualCheckInTime(actualCheckInTime)
                .actualCheckOutTime(actualCheckOutTime)
                .expectedCheckOutDate(expectedCheckOutDate)
                .build());
    }
}
