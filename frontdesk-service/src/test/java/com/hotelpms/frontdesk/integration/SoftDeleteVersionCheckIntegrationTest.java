package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import com.hotelpms.frontdesk.pricing.repository.RateSeasonRepository;
import com.hotelpms.frontdesk.quotations.domain.Quotation;
import com.hotelpms.frontdesk.quotations.domain.QuotationStatus;
import com.hotelpms.frontdesk.quotations.repository.QuotationRepository;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.rooms.domain.Room;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.domain.RoomType;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.rooms.repository.RoomTypeRepository;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.domain.TravellerType;
import com.hotelpms.frontdesk.stays.repository.StayGuestRepository;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.security.AlloggiatiCredentialEncryptor;
import com.hotelpms.frontdesk.stays.security.StayGuestDocumentEncryptor;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository-level regression test for GAP-18 (THREAT_MODEL.md): the
 * {@code @SQLDelete} override on {@link Reservation}, {@link Quotation},
 * {@link RateSeason}, {@link Stay} and {@link StayGuest} must actually enforce the
 * {@code version} predicate it declares, not just carry it as inert SQL text. For
 * {@code Stay} and {@code StayGuest} it must also bind both parameters at all: Hibernate 7
 * adds the version to the delete of a versioned entity, so a one-placeholder statement
 * failed with "Unable to bind parameter #2" and the removal of a stay guest returned 409.
 *
 * <p>Exercises the real {@code @SQLDelete} statement against a real
 * PostgreSQL database (Testcontainers, same setup as
 * {@link RoomTypeServiceIntegrationTest}) — no mocking of
 * {@code repository.delete()}. Each test reproduces the exact race GAP-18
 * describes: an entity is loaded (version N), a concurrent transaction bumps
 * the row's version to N+1 without that loaded instance knowing, then a
 * delete is attempted using the stale in-memory version. With
 * {@code verify = Expectation.RowCount.class} on the entity's {@code @SQLDelete},
 * Hibernate notices the 0-row update and raises a {@code StaleStateException}
 * that Spring translates to {@link ObjectOptimisticLockingFailureException}
 * (mapped to HTTP 409 {@code CONCURRENT_MODIFICATION} by
 * {@code GlobalExceptionHandler}). Without that flag (the pre-fix state),
 * the same stale delete silently affects 0 rows and reports success — these
 * tests fail if {@code verify = Expectation.RowCount.class} is removed from any
 * of the three entities.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=optional:configserver:",
                "CONFIG_SERVER_PASSWORD=ci-test-placeholder-only",
                "INTERNAL_HMAC_SECRET=test-integration-secret-only",
                "internal.hmac.secret=test-integration-secret-only",
                "management.tracing.enabled=false",
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "spring.cache.type=none",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SoftDeleteVersionCheckIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final int QUOTATION_VALIDITY_DAYS = 7;
    private static final String STALE_DELETE_MUST_BE_REJECTED =
            "A delete bound to a stale version must be rejected, not silently succeed";
    private static final String ROW_MUST_STILL_BE_ACTIVE =
            "The row must still be active — the rejected delete must not have taken effect";
    private static final String PARAM_ID = "id";
    private static final String TABLE_STAYS = "stays";
    private static final String TABLE_STAY_GUESTS = "stay_guests";
    private static final String GUEST_SURNAME = "Rossi";
    private static final String GENDER_MALE = "1";
    private static final String ISTAT_ITALY = "100000100";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1980, 1, 1);
    private static final int ROOM_CAPACITY = 2;
    private static final BigDecimal ROOM_PRICE = new BigDecimal("100.00");
    private static final int STAY_NIGHTS = 2;
    private static final int ROOM_SUFFIX_LENGTH = 8;
    private static final String PRIMARY_NAME = "Marco";
    private static final String SECOND_NAME = "Anna";

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private GuestClient guestClient;

    @MockitoBean
    private BillingClient billingClient;

    @MockitoBean
    private AlloggiatiWebSenderService alloggiatiWebSenderService;

    @MockitoBean
    private AlloggiatiCredentialEncryptor alloggiatiCredentialEncryptor;

    @MockitoBean
    private StayGuestDocumentEncryptor stayGuestDocumentEncryptor;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private RateSeasonRepository rateSeasonRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StayRepository stayRepository;

    @Autowired
    private StayGuestRepository stayGuestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /**
     * Bumps {@code version} directly in the database via a native SQL statement,
     * bypassing Hibernate's session entirely — simulates a concurrent transaction
     * (a different staff member's save) committing a change the caller's
     * in-memory entity instance never sees.
     *
     * @param table the table to bump {@code version} on
     * @param id    the row's primary key
     */
    private void simulateConcurrentVersionBump(final String table, final UUID id) {
        entityManager.createNativeQuery("UPDATE " + table + " SET version = version + 1 WHERE id = :id")
                .setParameter(PARAM_ID, id)
                .executeUpdate();
    }

    @Test
    @DisplayName("GAP-18: Reservation delete with a stale version raises 409 CONCURRENT_MODIFICATION, "
            + "not a silent no-op")
    void reservationDeleteFailsWhenVersionIsStale() {
        final Reservation created = reservationRepository.save(Reservation.builder()
                .hotelId(HOTEL_ID)
                .guestId(UUID.randomUUID())
                .expectedGuests(2)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .status(ReservationStatus.CONFIRMED)
                .build());
        final UUID id = created.getId();
        entityManager.flush();
        entityManager.clear();

        final Reservation stale = reservationRepository.findById(id).orElseThrow();
        simulateConcurrentVersionBump("reservations", id);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            reservationRepository.delete(stale);
            reservationRepository.flush();
        }, STALE_DELETE_MUST_BE_REJECTED);

        entityManager.clear();
        assertTrue(reservationRepository.findById(id).isPresent(),
                ROW_MUST_STILL_BE_ACTIVE);
    }

    @Test
    @DisplayName("GAP-18: Quotation delete with a stale version raises 409 CONCURRENT_MODIFICATION, "
            + "not a silent no-op")
    void quotationDeleteFailsWhenVersionIsStale() {
        final Quotation created = quotationRepository.save(Quotation.builder()
                .hotelId(HOTEL_ID)
                .prospectFirstName("Mario")
                .prospectLastName("Rossi")
                .prospectEmail("mario.rossi@example.test")
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(3))
                .expectedGuests(2)
                .status(QuotationStatus.DRAFT)
                .validUntil(LocalDate.now().plusDays(QUOTATION_VALIDITY_DAYS))
                .totalPrice(new BigDecimal("250.00"))
                .build());
        final UUID id = created.getId();
        entityManager.flush();
        entityManager.clear();

        final Quotation stale = quotationRepository.findById(id).orElseThrow();
        simulateConcurrentVersionBump("quotations", id);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            quotationRepository.delete(stale);
            quotationRepository.flush();
        }, STALE_DELETE_MUST_BE_REJECTED);

        entityManager.clear();
        assertTrue(quotationRepository.findById(id).isPresent(),
                ROW_MUST_STILL_BE_ACTIVE);
    }

    @Test
    @DisplayName("GAP-18: RateSeason delete with a stale version raises 409 CONCURRENT_MODIFICATION, "
            + "not a silent no-op")
    void rateSeasonDeleteFailsWhenVersionIsStale() {
        final RoomType roomType = roomTypeRepository.save(RoomType.builder()
                .hotelId(HOTEL_ID)
                .name("Deluxe Room " + UUID.randomUUID())
                .maxOccupancy(2)
                .basePrice(new BigDecimal("100.00"))
                .build());
        entityManager.flush();

        final RateSeason created = rateSeasonRepository.save(RateSeason.builder()
                .hotelId(HOTEL_ID)
                .roomTypeId(roomType.getId())
                .name("High season")
                .startDate(LocalDate.now().plusMonths(1))
                .endDate(LocalDate.now().plusMonths(2))
                .nightlyPrice(new BigDecimal("180.00"))
                .build());
        final UUID id = created.getId();
        entityManager.flush();
        entityManager.clear();

        final RateSeason stale = rateSeasonRepository.findById(id).orElseThrow();
        simulateConcurrentVersionBump("rate_seasons", id);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            rateSeasonRepository.delete(stale);
            rateSeasonRepository.flush();
        }, STALE_DELETE_MUST_BE_REJECTED);

        entityManager.clear();
        assertTrue(rateSeasonRepository.findById(id).isPresent(),
                ROW_MUST_STILL_BE_ACTIVE);
    }

    private StayGuest stayGuest(final Stay stay, final String firstName, final boolean primary) {
        return StayGuest.builder()
                .stay(stay)
                .firstName(firstName)
                .lastName(GUEST_SURNAME)
                .gender(GENDER_MALE)
                .dateOfBirth(BIRTH_DATE)
                .placeOfBirth(ISTAT_ITALY)
                .citizenship(ISTAT_ITALY)
                .isPrimaryGuest(primary)
                .arrivalDate(LocalDate.now())
                .travellerType(primary ? TravellerType.OSPITE_SINGOLO : TravellerType.FAMILIARE)
                .build();
    }

    /**
     * Persists the full chain a stay needs (room type, room, reservation) and a stay with a
     * primary guest and a second one, flushed. {@code getGuests().get(1)} is the removable one.
     *
     * @return the managed stay
     */
    private Stay seedStayWithTwoGuests() {
        final RoomType roomType = roomTypeRepository.save(RoomType.builder()
                .hotelId(HOTEL_ID)
                .name("Stay Room " + UUID.randomUUID())
                .maxOccupancy(ROOM_CAPACITY)
                .basePrice(ROOM_PRICE)
                .build());
        final Room room = roomRepository.save(Room.builder()
                .hotelId(HOTEL_ID)
                .roomNumber("S-" + UUID.randomUUID().toString().substring(0, ROOM_SUFFIX_LENGTH))
                .roomType(roomType)
                .status(RoomStatus.OCCUPIED)
                .build());
        final Reservation reservation = reservationRepository.save(Reservation.builder()
                .hotelId(HOTEL_ID)
                .guestId(UUID.randomUUID())
                .expectedGuests(ROOM_CAPACITY)
                .checkInDate(LocalDate.now())
                .checkOutDate(LocalDate.now().plusDays(STAY_NIGHTS))
                .status(ReservationStatus.CHECKED_IN)
                .build());
        final Stay stay = stayRepository.save(Stay.builder()
                .hotelId(HOTEL_ID)
                .reservationId(reservation.getId())
                .guestId(UUID.randomUUID())
                .roomId(room.getId())
                .status(StayStatus.CHECKED_IN)
                .actualCheckInTime(LocalDateTime.now())
                .expectedCheckOutDate(LocalDate.now().plusDays(STAY_NIGHTS))
                .build());
        stay.getGuests().add(stayGuest(stay, PRIMARY_NAME, true));
        stay.getGuests().add(stayGuest(stay, SECOND_NAME, false));
        stayRepository.saveAndFlush(stay);
        return stay;
    }

    /**
     * Reads the raw {@code active} flag and {@code version} of a row, bypassing the
     * {@code @SQLRestriction} that hides soft-deleted rows from every JPA query.
     *
     * @param table the table to read
     * @param id    the row's primary key
     * @return {@code [active, version]}
     */
    private Object[] activeAndVersion(final String table, final UUID id) {
        return (Object[]) entityManager
                .createNativeQuery("SELECT active, version FROM " + table + " WHERE id = :id")
                .setParameter(PARAM_ID, id)
                .getSingleResult();
    }

    @Test
    @DisplayName("Removing a stay guest soft-deletes the row and bumps its version "
            + "(Hibernate 7 binds both id and version; one placeholder made the removal fail with 409)")
    void stayGuestRemovalSoftDeletesTheRowAndBumpsItsVersion() {
        final Stay stay = seedStayWithTwoGuests();
        final StayGuest second = stay.getGuests().get(1);
        final UUID id = second.getId();
        final long versionBefore = second.getVersion();
        entityManager.flush();
        entityManager.clear();

        // The exact path of StayGuestServiceImpl.removeGuest: drop it from the collection and save.
        final Stay reloaded = stayRepository.findById(stay.getId()).orElseThrow();
        final StayGuest target = reloaded.getGuests().stream()
                .filter(guest -> guest.getId().equals(id)).findFirst().orElseThrow();
        reloaded.getGuests().remove(target);
        stayRepository.saveAndFlush(reloaded);
        entityManager.clear();

        final Object[] row = activeAndVersion(TABLE_STAY_GUESTS, id);
        assertFalse((Boolean) row[0], "The removed guest must be soft-deleted, not kept active");
        assertEquals(versionBefore + 1, ((Number) row[1]).longValue(), "Soft delete must bump the version");
        assertEquals(1, stayRepository.findById(stay.getId()).orElseThrow().getGuests().size(),
                "Only the primary guest must remain on the stay");
    }

    @Test
    @DisplayName("Stay guest delete with a stale version is rejected with CONCURRENT_MODIFICATION")
    void stayGuestDeleteFailsWhenVersionIsStale() {
        final Stay stay = seedStayWithTwoGuests();
        final UUID id = stay.getGuests().get(1).getId();
        entityManager.flush();
        entityManager.clear();

        final StayGuest stale = stayGuestRepository.findById(id).orElseThrow();
        simulateConcurrentVersionBump(TABLE_STAY_GUESTS, id);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            stayGuestRepository.delete(stale);
            stayGuestRepository.flush();
        }, STALE_DELETE_MUST_BE_REJECTED);

        entityManager.clear();
        assertTrue(stayGuestRepository.findById(id).isPresent(), ROW_MUST_STILL_BE_ACTIVE);
    }

    @Test
    @DisplayName("Stay delete soft-deletes the row, bumps its version and takes its guests with it")
    void staySoftDeleteBumpsItsVersionAndCascadesToItsGuests() {
        final Stay stay = seedStayWithTwoGuests();
        final UUID id = stay.getId();
        final long versionBefore = stay.getVersion();
        entityManager.flush();
        entityManager.clear();

        stayRepository.delete(stayRepository.findById(id).orElseThrow());
        stayRepository.flush();
        entityManager.clear();

        final Object[] row = activeAndVersion(TABLE_STAYS, id);
        assertFalse((Boolean) row[0], "The deleted stay must be soft-deleted");
        assertEquals(versionBefore + 1, ((Number) row[1]).longValue(), "Soft delete must bump the version");
        final long activeGuests = ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM " + TABLE_STAY_GUESTS
                        + " WHERE stay_id = :id AND active = true")
                .setParameter(PARAM_ID, id)
                .getSingleResult()).longValue();
        assertEquals(0L, activeGuests, "The guests of a deleted stay must be soft-deleted with it");
    }

    @Test
    @DisplayName("Stay delete with a stale version is rejected with CONCURRENT_MODIFICATION")
    void stayDeleteFailsWhenVersionIsStale() {
        final UUID id = seedStayWithTwoGuests().getId();
        entityManager.flush();
        entityManager.clear();

        final Stay stale = stayRepository.findById(id).orElseThrow();
        simulateConcurrentVersionBump(TABLE_STAYS, id);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            stayRepository.delete(stale);
            stayRepository.flush();
        }, STALE_DELETE_MUST_BE_REJECTED);

        entityManager.clear();
        assertTrue(stayRepository.findById(id).isPresent(), ROW_MUST_STILL_BE_ACTIVE);
    }
}
