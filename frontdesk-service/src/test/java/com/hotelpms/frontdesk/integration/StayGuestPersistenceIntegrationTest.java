package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
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
import com.hotelpms.frontdesk.stays.dto.StayGuestRequest;
import com.hotelpms.frontdesk.stays.dto.StayGuestResponse;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.security.AlloggiatiCredentialEncryptor;
import com.hotelpms.frontdesk.stays.security.StayGuestDocumentEncryptor;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import com.hotelpms.frontdesk.stays.service.StayGuestService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * The guest-lifecycle responses must describe what is actually persisted.
 *
 * <p>Found in a live check: {@code POST /stays/{id}/guests} answered {@code id: null,
 * version: null}, and {@code PUT /stays/{id}/guests/{guestId}} answered the version from before the
 * save. Two causes, both invisible to the Mockito unit tests (a mocked repository never merges or
 * flushes): {@code stayRepository.save(stay)} on an already-managed {@code Stay} is a {@code merge},
 * which copies a new child into the collection and leaves the caller's instance detached, and a
 * {@code @Version} only moves at flush, after the response had already been mapped.
 *
 * <p>Real PostgreSQL through Testcontainers, same setup as {@link SoftDeleteVersionCheckIntegrationTest}.
 * Each test clears the persistence context after seeding, so the service loads the stay the way a
 * fresh request would.
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
class StayGuestPersistenceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final String PARAM_ID = "id";
    private static final String GUEST_SURNAME = "Rossi";
    private static final String GENDER_MALE = "1";
    private static final String ISTAT_ITALY = "100000100";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1980, 1, 1);
    private static final int ROOM_CAPACITY = 3;
    private static final BigDecimal ROOM_PRICE = new BigDecimal("100.00");
    private static final int STAY_NIGHTS = 2;
    private static final int ROOM_SUFFIX_LENGTH = 8;
    private static final String PRIMARY_NAME = "Marco";
    private static final String SECOND_NAME = "Anna";
    private static final String ADDED_NAME = "Luca";
    private static final BigDecimal CITY_TAX_AMOUNT = new BigDecimal("3.50");

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

    @MockitoBean
    private CityTaxAssessmentService cityTaxAssessmentService;

    @Autowired
    private StayGuestService stayGuestService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StayRepository stayRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    @DisplayName("addGuest answers with the id and version of the row it persisted")
    void addGuestReturnsTheIdAndVersionOfThePersistedRow() {
        final Stay stay = seedStayWithTwoGuests();

        final StayGuestResponse response = stayGuestService.addGuest(stay.getId(), HOTEL_ID, request(ADDED_NAME));
        entityManager.flush();
        entityManager.clear();

        assertNotNull(response.id(), "The response must carry the new guest's id");
        assertNotNull(response.version(), "The response must carry the new guest's version");
        assertEquals(ADDED_NAME, storedGuest(response.id(), "first_name"));
        assertEquals(dbVersion(response.id()), response.version().longValue());
    }

    @Test
    @DisplayName("addGuest persists the city-tax charge stamped on the new guest, so removeGuest can void it later")
    void addGuestPersistsTheCityTaxChargeStampedOnTheNewGuest() {
        final Stay stay = seedStayWithTwoGuests();
        final UUID chargeId = UUID.randomUUID();
        doAnswer(invocation -> {
            final StayGuest added = invocation.getArgument(1);
            added.setCityTaxChargeId(chargeId);
            added.setCityTaxChargeAmount(CITY_TAX_AMOUNT);
            return null;
        }).when(cityTaxAssessmentService).rectifyForGuestAdded(any(Stay.class), any(StayGuest.class));

        stayGuestService.addGuest(stay.getId(), HOTEL_ID, request(ADDED_NAME));
        entityManager.flush();
        entityManager.clear();

        final StayGuest stored = stayRepository.findById(stay.getId()).orElseThrow().getGuests().stream()
                .filter(guest -> ADDED_NAME.equals(guest.getFirstName())).findFirst().orElseThrow();
        assertEquals(chargeId, stored.getCityTaxChargeId(),
                "The charge id stamped on the added guest must reach the database");
        assertEquals(0, CITY_TAX_AMOUNT.compareTo(stored.getCityTaxChargeAmount()));
    }

    @Test
    @DisplayName("updateGuest answers with the version that is stored after the save")
    void updateGuestReturnsTheVersionStoredAfterTheSave() {
        final Stay stay = seedStayWithTwoGuests();
        final UUID guestId = stay.getGuests().get(1).getId();
        entityManager.flush();
        entityManager.clear();

        final StayGuestResponse response = stayGuestService.updateGuest(
                stay.getId(), guestId, HOTEL_ID, request("Annamaria"));
        entityManager.flush();
        entityManager.clear();

        assertNotNull(response.version());
        assertEquals(dbVersion(guestId), response.version().longValue(),
                "A client that reuses the returned version must not get a false stale-version conflict");
    }

    @Test
    @DisplayName("recordDeparture answers with the version that is stored after the save")
    void recordDepartureReturnsTheVersionStoredAfterTheSave() {
        final Stay stay = seedStayWithTwoGuests();
        final UUID guestId = stay.getGuests().get(1).getId();
        entityManager.flush();
        entityManager.clear();

        final StayGuestResponse response = stayGuestService.recordDeparture(
                stay.getId(), guestId, HOTEL_ID, LocalDate.now().plusDays(1));
        entityManager.flush();
        entityManager.clear();

        assertNotNull(response.version());
        assertEquals(dbVersion(guestId), response.version().longValue());
    }

    @Test
    @DisplayName("promotePrimary answers with the version that is stored after the save")
    void promotePrimaryReturnsTheVersionStoredAfterTheSave() {
        final Stay stay = seedStayWithTwoGuests();
        final UUID guestId = stay.getGuests().get(1).getId();
        entityManager.flush();
        entityManager.clear();

        final StayGuestResponse response = stayGuestService.promotePrimary(stay.getId(), guestId, HOTEL_ID);
        entityManager.flush();
        entityManager.clear();

        assertNotNull(response.version());
        assertEquals(dbVersion(guestId), response.version().longValue());
        assertTrue(response.isPrimaryGuest());
    }

    private long dbVersion(final UUID guestId) {
        return ((Number) entityManager
                .createNativeQuery("SELECT version FROM stay_guests WHERE id = :id")
                .setParameter(PARAM_ID, guestId)
                .getSingleResult()).longValue();
    }

    private Object storedGuest(final UUID guestId, final String column) {
        return entityManager
                .createNativeQuery("SELECT " + column + " FROM stay_guests WHERE id = :id")
                .setParameter(PARAM_ID, guestId)
                .getSingleResult();
    }

    private StayGuestRequest request(final String firstName) {
        return new StayGuestRequest(firstName, GUEST_SURNAME, GENDER_MALE, BIRTH_DATE, ISTAT_ITALY, ISTAT_ITALY,
                null, null, null, false, TravellerType.FAMILIARE, null, null);
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
     * Persists the chain a stay needs (room type, room, reservation) and a {@code CHECKED_IN} stay with a
     * primary guest and a second one, flushed and detached so the service sees it as a fresh request would.
     *
     * @return the stay, detached; {@code getGuests().get(1)} is the non-primary guest
     */
    private Stay seedStayWithTwoGuests() {
        final RoomType roomType = roomTypeRepository.save(RoomType.builder()
                .hotelId(HOTEL_ID)
                .name("Guest Room " + UUID.randomUUID())
                .maxOccupancy(ROOM_CAPACITY)
                .basePrice(ROOM_PRICE)
                .build());
        final Room room = roomRepository.save(Room.builder()
                .hotelId(HOTEL_ID)
                .roomNumber("G-" + UUID.randomUUID().toString().substring(0, ROOM_SUFFIX_LENGTH))
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
        entityManager.clear();
        return stay;
    }
}
