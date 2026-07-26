package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.stays.domain.AlloggiatiComune;
import com.hotelpms.frontdesk.stays.repository.AlloggiatiComuneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-3 (LIVE_E2E_AUDIT_2026-07.md): {@link AlloggiatiComuneRepository#searchActive} ordered
 * results alphabetically with a hard {@code LIMIT 20}, so a query for "Roma" never surfaced
 * "ROMA" itself — ~19 alphabetically earlier comuni (Arcinazzo Romano, Montorio Romano, ...)
 * filled the page first. This test seeds exactly that scenario against a real PostgreSQL
 * database and asserts the exact match now ranks first, ahead of the cap.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true"
})
class AlloggiatiComuneRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final String PROVINCIA_RM = "RM";
    private static final String DESC_ROMA = "ROMA";
    private static final int AUTOCOMPLETE_MAX_RESULTS = 20;

    @Autowired
    private AlloggiatiComuneRepository comuneRepository;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void seedComuni() {
        // 20 alphabetically-earlier "Roma"-derived comuni — enough to exhaust the
        // autocomplete cap (AlloggiatiLookupServiceImpl.AUTOCOMPLETE_MAX_RESULTS = 20)
        // before the alphabet reaches "ROMA" itself.
        for (int i = 0; i < AUTOCOMPLETE_MAX_RESULTS; i++) {
            comuneRepository.save(AlloggiatiComune.builder()
                    .codice("A" + String.format("%08d", i))
                    .descrizione("Arcinazzo Romano " + i)
                    .provincia(PROVINCIA_RM)
                    .build());
        }
        comuneRepository.save(AlloggiatiComune.builder()
                .codice("058091000")
                .descrizione(DESC_ROMA)
                .provincia(PROVINCIA_RM)
                .build());
    }

    @Test
    @DisplayName("BUG-3: an exact match ('ROMA') ranks first and survives the 20-result cap "
            + "even with 20 alphabetically-earlier substring matches present")
    void exactMatchRanksFirstAndSurvivesCap() {
        final List<AlloggiatiComune> results = comuneRepository.searchActive(
                "Roma", null, LocalDate.now(), PageRequest.of(0, AUTOCOMPLETE_MAX_RESULTS));

        assertEquals(AUTOCOMPLETE_MAX_RESULTS, results.size());
        assertEquals(DESC_ROMA, results.get(0).getDescrizione(),
                "Exact match must be ranked ahead of alphabetically-earlier substring matches");
    }

    @Test
    @DisplayName("Prefix match ranks ahead of substring-only match, alphabetical order is the final tie-break")
    void prefixMatchRanksAheadOfSubstringMatch() {
        comuneRepository.save(AlloggiatiComune.builder()
                .codice("099999999")
                .descrizione("Romano di Lombardia")
                .provincia(PROVINCIA_RM)
                .build());

        final List<AlloggiatiComune> results =
                comuneRepository.searchActive("roma", null, LocalDate.now(), PageRequest.of(0, 30));

        assertTrue(results.indexOf(results.stream()
                        .filter(c -> DESC_ROMA.equals(c.getDescrizione())).findFirst().orElseThrow())
                < results.indexOf(results.stream()
                        .filter(c -> "Romano di Lombardia".equals(c.getDescrizione())).findFirst().orElseThrow()),
                "Exact match must rank ahead of a prefix match");
        assertTrue(results.stream().anyMatch(c -> "Arcinazzo Romano 0".equals(c.getDescrizione())),
                "Substring-only matches still appear, just ranked last");
    }
}
