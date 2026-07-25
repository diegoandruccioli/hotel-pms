package com.hotelpms.guest.integration;

import com.hotelpms.guest.model.Guest;
import com.hotelpms.guest.repository.GuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P6 (Sprint 1 audit, 2026-07-25): guest-service had no Testcontainers test, so
 * Flyway V7 ({@code V7__add_trgm_search_indexes.sql}) — the one migration that
 * requires the privileged {@code CREATE EXTENSION pg_trgm} — was never validated
 * against a real PostgreSQL role. It works today only because the dev/CI Postgres
 * container runs as superuser; a managed Postgres where the app role lacks
 * extension-creation rights would fail silently (no test would catch it).
 *
 * <p>This test runs the real migration against a fresh {@code postgres:16-alpine}
 * container (not superuser-privileged by assumption, just the same image used
 * elsewhere in this repo) and exercises {@link GuestRepository#searchByKeywordAndHotelId}
 * — the query the {@code pg_trgm} GIN indexes exist to speed up — end to end.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true"
})
class GuestSearchIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_guest_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final UUID OTHER_HOTEL_ID = UUID.randomUUID();

    @Autowired
    private GuestRepository guestRepository;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void seedGuests() {
        guestRepository.save(Guest.builder()
                .firstName("Mario")
                .lastName("Rossi")
                .email("mario.rossi@example.com")
                .city("Firenze")
                .hotelId(HOTEL_ID)
                .gdprConsentDate(LocalDate.now())
                .build());
        guestRepository.save(Guest.builder()
                .firstName("Luigi")
                .lastName("Verdi")
                .email("luigi.verdi@example.com")
                .city("Roma")
                .hotelId(HOTEL_ID)
                .gdprConsentDate(LocalDate.now())
                .build());
        // Same city, different hotel — must never leak into hotel-scoped results.
        guestRepository.save(Guest.builder()
                .firstName("Anna")
                .lastName("Bianchi")
                .email("anna.bianchi@example.com")
                .city("Firenze")
                .hotelId(OTHER_HOTEL_ID)
                .gdprConsentDate(LocalDate.now())
                .build());
    }

    @Test
    @DisplayName("V7 migration applies: CREATE EXTENSION pg_trgm + 4 GIN indexes succeed against real Postgres")
    void migrationAppliesAndExtensionIsCreated() {
        assertEquals(2, guestRepository.findAllByHotelId(HOTEL_ID, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    @DisplayName("Case-insensitive substring search on first/last name matches, scoped to the hotel")
    void searchMatchesByNameCaseInsensitive() {
        final Page<Guest> byFirstName =
                guestRepository.searchByKeywordAndHotelId("mario", HOTEL_ID, PageRequest.of(0, 10));
        assertEquals(1, byFirstName.getTotalElements());
        assertEquals("Rossi", byFirstName.getContent().get(0).getLastName());

        final Page<Guest> byLastName =
                guestRepository.searchByKeywordAndHotelId("VERDI", HOTEL_ID, PageRequest.of(0, 10));
        assertEquals(1, byLastName.getTotalElements());
    }

    @Test
    @DisplayName("Search on city is hotel-scoped: same city in another hotel never leaks in")
    void searchOnCityDoesNotLeakAcrossHotels() {
        final Page<Guest> result =
                guestRepository.searchByKeywordAndHotelId("firenze", HOTEL_ID, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().stream().allMatch(g -> HOTEL_ID.equals(g.getHotelId())));
    }
}
