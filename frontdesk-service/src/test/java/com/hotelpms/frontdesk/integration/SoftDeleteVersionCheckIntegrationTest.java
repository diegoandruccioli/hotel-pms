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
import com.hotelpms.frontdesk.rooms.domain.RoomType;
import com.hotelpms.frontdesk.rooms.repository.RoomTypeRepository;
import com.hotelpms.frontdesk.stays.security.AlloggiatiCredentialEncryptor;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository-level regression test for GAP-18 (THREAT_MODEL.md): the
 * {@code @SQLDelete} override on {@link Reservation}, {@link Quotation} and
 * {@link RateSeason} must actually enforce the {@code version} predicate it
 * declares, not just carry it as inert SQL text.
 *
 * <p>Exercises the real {@code @SQLDelete} statement against a real
 * PostgreSQL database (Testcontainers, same setup as
 * {@link RoomTypeServiceIntegrationTest}) — no mocking of
 * {@code repository.delete()}. Each test reproduces the exact race GAP-18
 * describes: an entity is loaded (version N), a concurrent transaction bumps
 * the row's version to N+1 without that loaded instance knowing, then a
 * delete is attempted using the stale in-memory version. With
 * {@code check = ResultCheckStyle.COUNT} on the entity's {@code @SQLDelete},
 * Hibernate notices the 0-row update and raises a {@code StaleStateException}
 * that Spring translates to {@link ObjectOptimisticLockingFailureException}
 * (mapped to HTTP 409 {@code CONCURRENT_MODIFICATION} by
 * {@code GlobalExceptionHandler}). Without that flag (the pre-fix state),
 * the same stale delete silently affects 0 rows and reports success — these
 * tests fail if {@code check = ResultCheckStyle.COUNT} is removed from any
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
                "spring.cache.type=none"
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

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private RateSeasonRepository rateSeasonRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

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
                .setParameter("id", id)
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
}
