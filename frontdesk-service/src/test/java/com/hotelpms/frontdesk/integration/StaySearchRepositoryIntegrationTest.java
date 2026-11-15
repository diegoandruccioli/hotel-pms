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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link StayRepository#searchByHotelId} against a real PostgreSQL database: the query mixes a status
 * set, {@code NULL}-skippable departure-day and text filters, a failed-only switch and a
 * {@code LIKE ... ESCAPE} over two nullable denormalized columns, plus the tenant scope. A mocked
 * repository proves none of it.
 */
@AutoConfigureCache
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
// StayGuestDocumentEncryptor: see StayOccupancyRepositoryIntegrationTest. Hibernate needs it to
// bootstrap the EntityManagerFactory.
@Import(StayGuestDocumentEncryptor.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true",
        "frontdesk.documents.encryption-key=test-encryption-key",
        "frontdesk.documents.encryption-salt=deadbeefdeadbeefdeadbeefdeadbeef"
})
class StaySearchRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final int PAGE_SIZE = 50;
    private static final int MAX_OCCUPANCY = 2;
    private static final BigDecimal BASE_PRICE = new BigDecimal("90.00");
    private static final LocalDate CHECK_OUT_DAY = LocalDate.of(2026, 11, 16);
    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 11, 14, 14, 0);
    private static final Set<StayStatus> ANY_STATUS = EnumSet.allOf(StayStatus.class);

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

    private Seed seed() {
        final UUID hotelId = UUID.randomUUID();
        final Seed seed = new Seed(
                hotelId,
                save(hotelId, "Rossi Mario", "101", StayStatus.CHECKED_IN, CHECK_OUT_DAY, false),
                save(hotelId, "Bianchi Anna", "202", StayStatus.CHECKED_OUT, CHECK_OUT_DAY.minusDays(1), true),
                save(hotelId, "100% Verdi", "S_3", StayStatus.CHECKED_OUT, CHECK_OUT_DAY, false),
                // A legacy stay: no denormalized name or room number.
                save(hotelId, null, null, StayStatus.CHECKED_OUT, CHECK_OUT_DAY, false));
        // Must never match: another hotel's stay.
        save(UUID.randomUUID(), "Rossi Mario", "101", StayStatus.CHECKED_IN, CHECK_OUT_DAY, false);
        return seed;
    }

    private Stay save(
            final UUID hotelId, final String guestName, final String roomNumber, final StayStatus status,
            final LocalDate expectedCheckOut, final boolean sendFailed) {
        final UUID guestId = UUID.randomUUID();
        final RoomType roomType = roomTypeRepository.saveAndFlush(RoomType.builder()
                .hotelId(hotelId).name("Standard-" + UUID.randomUUID())
                .maxOccupancy(MAX_OCCUPANCY).basePrice(BASE_PRICE).build());
        final Room room = roomRepository.saveAndFlush(Room.builder()
                .hotelId(hotelId).roomNumber("R-" + UUID.randomUUID().toString().substring(0, 8))
                .roomType(roomType).status(RoomStatus.OCCUPIED).build());
        final Reservation reservation = reservationRepository.saveAndFlush(Reservation.builder()
                .hotelId(hotelId).guestId(guestId).expectedGuests(1)
                .checkInDate(CHECK_IN.toLocalDate()).checkOutDate(expectedCheckOut)
                .status(ReservationStatus.CHECKED_IN).build());
        return stayRepository.saveAndFlush(Stay.builder()
                .hotelId(hotelId).reservationId(reservation.getId()).guestId(guestId).roomId(room.getId())
                .status(status).actualCheckInTime(CHECK_IN).expectedCheckOutDate(expectedCheckOut)
                .guestDisplayName(guestName).roomNumber(roomNumber).alloggiatiSendFailed(sendFailed)
                .build());
    }

    private Set<UUID> search(
            final Seed seed, final Set<StayStatus> statuses, final LocalDate departingOn,
            final boolean failedOnly, final String pattern) {
        final Pageable pageable = PageRequest.of(0, PAGE_SIZE);
        return stayRepository.searchByHotelId(seed.hotelId(), statuses, departingOn, failedOnly, pattern, pageable)
                .stream().map(Stay::getId).collect(Collectors.toSet());
    }

    private static Set<UUID> ids(final Stay... stays) {
        return Arrays.stream(stays).map(Stay::getId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("With no filter it returns every stay of the hotel, legacy ones included, and nothing else")
    void noFilterReturnsEveryStayOfTheHotel() {
        final Seed s = seed();

        assertEquals(ids(s.rossi(), s.bianchi(), s.verdi(), s.legacy()), search(s, ANY_STATUS, null, false, null));
    }

    @Test
    @DisplayName("The pattern matches the guest name or the room number, whatever the case")
    void thePatternMatchesNameOrRoom() {
        final Seed s = seed();

        assertEquals(ids(s.rossi()), search(s, ANY_STATUS, null, false, "%rossi%"));
        assertEquals(ids(s.bianchi()), search(s, ANY_STATUS, null, false, "%202%"));
        assertEquals(Set.of(), search(s, ANY_STATUS, null, false, "%zzz%"));
    }

    @Test
    @DisplayName("An escaped percent or underscore matches literally instead of acting as a wildcard")
    void escapedWildcardsMatchLiterally() {
        final Seed s = seed();

        assertEquals(ids(s.verdi()), search(s, ANY_STATUS, null, false, "%100!%%"));
        assertEquals(ids(s.verdi()), search(s, ANY_STATUS, null, false, "%s!_3%"));
        assertEquals(Set.of(), search(s, ANY_STATUS, null, false, "%s!_4%"));
    }

    @Test
    @DisplayName("Status, departure day and failed-only combine with the text filter")
    void filtersCombine() {
        final Seed s = seed();

        assertEquals(ids(s.verdi(), s.legacy()),
                search(s, EnumSet.of(StayStatus.CHECKED_OUT), CHECK_OUT_DAY, false, null));
        assertEquals(ids(s.bianchi()), search(s, ANY_STATUS, null, true, null));
        assertEquals(ids(s.rossi()), search(s, EnumSet.of(StayStatus.CHECKED_IN), CHECK_OUT_DAY, false, "%mario%"));
        assertEquals(Set.of(), search(s, EnumSet.of(StayStatus.EXPECTED), null, false, null));
    }

    private record Seed(UUID hotelId, Stay rossi, Stay bianchi, Stay verdi, Stay legacy) {
    }
}
