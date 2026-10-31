package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationLineItem;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.rooms.domain.Room;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.domain.RoomType;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.rooms.repository.RoomTypeRepository;
import com.hotelpms.frontdesk.stays.security.StayGuestDocumentEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReservationRepository#existsUpcomingReservationByHotelIdAndRoomId} against a real
 * PostgreSQL database: it backs the guard that refuses to delete a room still held by a
 * reservation that has not ended, so the join through {@code lineItems}, the status list, the
 * date cut-off and the tenant scope must all be right — a mocked repository proves none of them.
 *
 * <p>A room with a non-terminal reservation ending after today blocks the deletion; a terminal
 * status, a reservation already over, a soft-deleted line item, another room or another hotel must not.
 */
@AutoConfigureCache
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
// StayGuestDocumentEncryptor: see StayOccupancyRepositoryIntegrationTest — Hibernate needs it to
// bootstrap the EntityManagerFactory even when no stay entity is exercised.
@Import(StayGuestDocumentEncryptor.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true",
        "frontdesk.documents.encryption-key=test-encryption-key",
        "frontdesk.documents.encryption-salt=deadbeefdeadbeefdeadbeefdeadbeef"
})
class ReservationRoomGuardRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final BigDecimal BASE_PRICE = new BigDecimal("90.00");
    private static final int MAX_OCCUPANCY = 2;
    private static final int ROOM_SUFFIX_LENGTH = 8;
    private static final long DAYS_AHEAD = 10L;
    private static final long STAY_LENGTH_DAYS = 2L;
    private static final String MESSAGE_BLOCKS = "A room held by this reservation must not be deletable";
    private static final String MESSAGE_FREE = "This reservation must not hold the room";

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    @DisplayName("A confirmed reservation in the future holds the room")
    void aFutureConfirmedReservationHoldsTheRoom() {
        final UUID hotelId = UUID.randomUUID();
        final UUID roomId = seedRoom(hotelId);
        seedReservation(hotelId, roomId, ReservationStatus.CONFIRMED, futureStart(), true);

        assertTrue(exists(hotelId, roomId), MESSAGE_BLOCKS);
    }

    @Test
    @DisplayName("Every non-terminal status holds the room: pending, partially checked in, checked in")
    void everyNonTerminalStatusHoldsTheRoom() {
        final UUID hotelId = UUID.randomUUID();
        for (final ReservationStatus status : new ReservationStatus[] {
                ReservationStatus.PENDING, ReservationStatus.PARTIALLY_CHECKED_IN, ReservationStatus.CHECKED_IN}) {
            final UUID roomId = seedRoom(hotelId);
            seedReservation(hotelId, roomId, status, futureStart(), true);

            assertTrue(exists(hotelId, roomId), MESSAGE_BLOCKS + ": " + status);
        }
    }

    @Test
    @DisplayName("A reservation that is over, cancelled or a no-show does not hold the room")
    void terminalReservationsDoNotHoldTheRoom() {
        final UUID hotelId = UUID.randomUUID();
        for (final ReservationStatus status : new ReservationStatus[] {
                ReservationStatus.CHECKED_OUT, ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW}) {
            final UUID roomId = seedRoom(hotelId);
            seedReservation(hotelId, roomId, status, futureStart(), true);

            assertFalse(exists(hotelId, roomId), MESSAGE_FREE + ": " + status);
        }
    }

    @Test
    @DisplayName("A never-confirmed reservation whose dates are already over does not hold the room")
    void aStaleReservationAlreadyOverDoesNotHoldTheRoom() {
        final UUID hotelId = UUID.randomUUID();
        final UUID roomId = seedRoom(hotelId);
        seedReservation(hotelId, roomId, ReservationStatus.CONFIRMED,
                LocalDate.now().minusDays(DAYS_AHEAD), true);

        assertFalse(exists(hotelId, roomId), MESSAGE_FREE);
    }

    @Test
    @DisplayName("A soft-deleted line item no longer holds the room")
    void aSoftDeletedLineItemDoesNotHoldTheRoom() {
        final UUID hotelId = UUID.randomUUID();
        final UUID roomId = seedRoom(hotelId);
        seedReservation(hotelId, roomId, ReservationStatus.CONFIRMED, futureStart(), false);

        assertFalse(exists(hotelId, roomId), MESSAGE_FREE);
    }

    @Test
    @DisplayName("Another room of the same hotel, or the same room id seen from another hotel, is not held")
    void otherRoomsAndOtherHotelsAreNotHeld() {
        final UUID hotelId = UUID.randomUUID();
        final UUID heldRoom = seedRoom(hotelId);
        final UUID otherRoom = seedRoom(hotelId);
        seedReservation(hotelId, heldRoom, ReservationStatus.CONFIRMED, futureStart(), true);

        assertFalse(exists(hotelId, otherRoom), "A different room must stay deletable");
        assertFalse(exists(UUID.randomUUID(), heldRoom), "Another hotel must not see this hotel's reservation");
    }

    private boolean exists(final UUID hotelId, final UUID roomId) {
        return reservationRepository.existsUpcomingReservationByHotelIdAndRoomId(hotelId, roomId, LocalDate.now());
    }

    private static LocalDate futureStart() {
        return LocalDate.now().plusDays(DAYS_AHEAD);
    }

    private UUID seedRoom(final UUID hotelId) {
        final RoomType roomType = roomTypeRepository.saveAndFlush(RoomType.builder()
                .hotelId(hotelId)
                .name("Guard-" + UUID.randomUUID())
                .maxOccupancy(MAX_OCCUPANCY)
                .basePrice(BASE_PRICE)
                .build());
        return roomRepository.saveAndFlush(Room.builder()
                .hotelId(hotelId)
                .roomNumber("G-" + UUID.randomUUID().toString().substring(0, ROOM_SUFFIX_LENGTH))
                .roomType(roomType)
                .status(RoomStatus.CLEAN)
                .build()).getId();
    }

    /**
     * Persists a reservation of {@link #STAY_LENGTH_DAYS} nights starting at {@code checkIn}, holding
     * {@code roomId} through one line item.
     *
     * @param hotelId        the hotel the reservation belongs to
     * @param roomId         the room its line item holds
     * @param status         the reservation status
     * @param checkIn        the first night
     * @param lineItemActive whether the line item is active ({@code false} = soft-deleted)
     */
    private void seedReservation(final UUID hotelId, final UUID roomId, final ReservationStatus status,
            final LocalDate checkIn, final boolean lineItemActive) {
        final Reservation reservation = Reservation.builder()
                .hotelId(hotelId)
                .guestId(UUID.randomUUID())
                .expectedGuests(1)
                .checkInDate(checkIn)
                .checkOutDate(checkIn.plusDays(STAY_LENGTH_DAYS))
                .status(status)
                .lineItems(new ArrayList<>())
                .build();
        reservation.getLineItems().add(ReservationLineItem.builder()
                .reservation(reservation)
                .roomId(roomId)
                .price(BASE_PRICE)
                .active(lineItemActive)
                .build());
        reservationRepository.saveAndFlush(reservation);
    }
}
