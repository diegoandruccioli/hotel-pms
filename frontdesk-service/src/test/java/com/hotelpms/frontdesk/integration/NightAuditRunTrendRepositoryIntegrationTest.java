package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.nightaudit.domain.NightAuditRun;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditStatus;
import com.hotelpms.frontdesk.nightaudit.repository.NightAuditRunRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real-PostgreSQL coverage of
 * {@link NightAuditRunRepository#findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc}:
 * hotel scoping, the COMPLETED filter, inclusive window ends and ascending order.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
// See StayOccupancyRepositoryIntegrationTest: Hibernate needs this converter bean to bootstrap.
@Import(StayGuestDocumentEncryptor.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true",
        "frontdesk.documents.encryption-key=test-encryption-key",
        "frontdesk.documents.encryption-salt=deadbeefdeadbeefdeadbeefdeadbeef"
})
class NightAuditRunTrendRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final UUID OTHER_HOTEL_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 9, 28);
    private static final LocalDate TO = LocalDate.of(2026, 10, 3);

    @Autowired
    private NightAuditRunRepository repository;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private NightAuditRun save(final UUID hotelId, final LocalDate date, final NightAuditStatus status) {
        return repository.saveAndFlush(NightAuditRun.builder()
                .hotelId(hotelId)
                .businessDate(date)
                .status(status)
                .startedAt(LocalDateTime.now())
                .runBy("system")
                .arrivals(1)
                .departures(1)
                .guestsInHouse(1L)
                .availableRooms(1)
                .build());
    }

    private List<LocalDate> trendDates(final UUID hotelId) {
        return repository
                .findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc(
                        hotelId, NightAuditStatus.COMPLETED, FROM, TO)
                .stream()
                .map(NightAuditRun::getBusinessDate)
                .toList();
    }

    @Test
    void returnsOnlyCompletedRunsOfTheHotelOldestFirst() {
        save(HOTEL_ID, TO, NightAuditStatus.COMPLETED);
        save(HOTEL_ID, FROM.plusDays(2), NightAuditStatus.COMPLETED);
        save(HOTEL_ID, FROM.plusDays(1), NightAuditStatus.FAILED);
        save(OTHER_HOTEL_ID, FROM.plusDays(3), NightAuditStatus.COMPLETED);

        assertEquals(List.of(FROM.plusDays(2), TO), trendDates(HOTEL_ID));
    }

    @Test
    void includesBothEndsOfTheWindowAndExcludesTheDaysOutsideIt() {
        save(HOTEL_ID, FROM.minusDays(1), NightAuditStatus.COMPLETED);
        save(HOTEL_ID, FROM, NightAuditStatus.COMPLETED);
        save(HOTEL_ID, TO, NightAuditStatus.COMPLETED);
        save(HOTEL_ID, TO.plusDays(1), NightAuditStatus.COMPLETED);

        assertEquals(List.of(FROM, TO), trendDates(HOTEL_ID));
    }

    @Test
    void returnsNothingForAHotelWithoutRuns() {
        save(HOTEL_ID, FROM, NightAuditStatus.COMPLETED);

        assertEquals(List.of(), trendDates(OTHER_HOTEL_ID));
    }
}
