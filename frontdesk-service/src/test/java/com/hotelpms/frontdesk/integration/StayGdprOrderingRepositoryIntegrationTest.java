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
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.security.StayGuestDocumentEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage, against a real PostgreSQL database, for the {@code NULLS LAST} fix on
 * {@link StayRepository}'s two GDPR legal-hold "most recent stay" lookups (T-GST-05).
 *
 * <p>{@code actual_check_in_time} is nullable in the schema. Postgres sorts {@code NULL} first
 * on {@code ORDER BY ... DESC}, so a plain derived-query {@code OrderBy} would let a
 * legacy/imported stay with no check-in time outrank a real one — understating how long a
 * guest's data has actually been held, or clearing the retention hold on the wrong stay. A
 * mocked-repository unit test (see {@code StayServiceImplTest}) cannot exercise this: the mock
 * returns whatever the test stubs, it never runs Postgres's own {@code NULLS FIRST} default. Both
 * queries were converted to native SQL with an explicit {@code NULLS LAST} — this test seeds a
 * real null-check-in row next to a real dated one and asserts the dated one wins, against the
 * actual database engine, not a mock.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
// StayGuestDocumentEncryptor: @DataJpaTest's minimal context doesn't scan plain
// @Component beans, but Hibernate needs it (via StayGuestDocumentNumberConverter,
// @Convert on StayGuest.documentNumber) to bootstrap the EntityManagerFactory for
// this persistence unit, regardless of which entity this test actually exercises.
@Import(StayGuestDocumentEncryptor.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true",
        "frontdesk.documents.encryption-key=test-encryption-key",
        "frontdesk.documents.encryption-salt=deadbeefdeadbeefdeadbeefdeadbeef"
})
class StayGdprOrderingRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final BigDecimal BASE_PRICE = new BigDecimal("90.00");
    private static final int MAX_OCCUPANCY = 2;
    private static final int REAL_CHECK_IN_YEAR = 2026;
    private static final int REAL_CHECK_IN_MONTH = 3;
    private static final int REAL_CHECK_IN_DAY = 1;
    private static final LocalDateTime REAL_CHECK_IN =
            LocalDate.of(REAL_CHECK_IN_YEAR, REAL_CHECK_IN_MONTH, REAL_CHECK_IN_DAY).atStartOfDay();

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
    void findTopByGuestIdAndHotelIdOrderByActualCheckInTimeDescIdDescPrefersTheRealCheckInOverANullOne() {
        final UUID hotelId = UUID.randomUUID();
        final UUID guestId = UUID.randomUUID();
        final UUID roomId = seedRoom(hotelId);
        seedStay(hotelId, roomId, guestId, StayStatus.CHECKED_OUT, REAL_CHECK_IN);
        // A legacy/imported row with no check-in time — Postgres would sort this first on a
        // plain "ORDER BY actual_check_in_time DESC" (NULLS FIRST is the default), which is
        // exactly the bug this test guards against.
        seedStay(hotelId, roomId, guestId, StayStatus.EXPECTED, null);

        final Optional<Stay> result =
                stayRepository.findTopByGuestIdAndHotelIdOrderByActualCheckInTimeDescIdDesc(guestId, hotelId);

        assertTrue(result.isPresent());
        assertEquals(REAL_CHECK_IN, result.get().getActualCheckInTime(),
                "the null-check-in row outranked the real one — NULLS LAST regressed");
    }

    @Test
    void findTopByGuestIdAndHotelIdAndStatusOrderByActualCheckInTimeDescIdDescPrefersTheRealCheckInOverANullOne() {
        final UUID hotelId = UUID.randomUUID();
        final UUID guestId = UUID.randomUUID();
        final UUID roomId = seedRoom(hotelId);
        seedStay(hotelId, roomId, guestId, StayStatus.CHECKED_OUT, REAL_CHECK_IN);
        // Same guest, same status, no check-in time — must still lose to the real one above.
        seedStay(hotelId, roomId, guestId, StayStatus.CHECKED_OUT, null);

        final Optional<Stay> result = stayRepository.findTopByGuestIdAndHotelIdAndStatusOrderByActualCheckInTimeDescIdDesc(
                guestId, hotelId, StayStatus.CHECKED_OUT.name());

        assertTrue(result.isPresent());
        assertEquals(REAL_CHECK_IN, result.get().getActualCheckInTime(),
                "the null-check-in row outranked the real one — NULLS LAST regressed");
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

    private void seedStay(final UUID hotelId, final UUID roomId, final UUID guestId,
            final StayStatus status, final LocalDateTime actualCheckInTime) {
        final LocalDate placeholderCheckIn = LocalDate.now();
        final Reservation reservation = reservationRepository.saveAndFlush(Reservation.builder()
                .hotelId(hotelId)
                .guestId(guestId)
                .expectedGuests(1)
                .checkInDate(placeholderCheckIn)
                .checkOutDate(placeholderCheckIn.plusDays(1))
                .status(ReservationStatus.CHECKED_IN)
                .build());

        stayRepository.saveAndFlush(Stay.builder()
                .hotelId(hotelId)
                .reservationId(reservation.getId())
                .guestId(guestId)
                .roomId(roomId)
                .status(status)
                .actualCheckInTime(actualCheckInTime)
                .expectedCheckOutDate(placeholderCheckIn.plusDays(1))
                .build());
    }
}
