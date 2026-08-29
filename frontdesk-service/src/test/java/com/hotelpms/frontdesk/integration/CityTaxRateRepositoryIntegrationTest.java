package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.citytax.domain.CityTaxRate;
import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import com.hotelpms.frontdesk.citytax.dto.CityTaxRateRequest;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryRequest;
import com.hotelpms.frontdesk.citytax.repository.CityTaxRateRepository;
import com.hotelpms.frontdesk.citytax.repository.HotelCategoryHistoryRepository;
import com.hotelpms.frontdesk.citytax.service.impl.CityTaxRateAdminServiceImpl;
import com.hotelpms.frontdesk.citytax.service.impl.HotelCategoryHistoryServiceImpl;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiComune;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.repository.AlloggiatiComuneRepository;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code excl_city_tax_rates_no_overlap} and
 * {@code excl_hotel_category_no_overlap} GiST exclusion constraints (V16/V17)
 * against a real PostgreSQL database — a Mockito-mocked repository cannot
 * exercise a DB-level constraint, and this class of bug (an overlap silently
 * accepted because a unit test never actually hits Postgres) is exactly what
 * bit {@code rate_seasons} before its own equivalent test was added.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true"
})
class CityTaxRateRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final String COMUNE_CODICE = "099014000";
    private static final String COMUNE_DESCRIZIONE = "Cattolica";
    private static final String PROVINCIA_RN = "RN";
    private static final String CATEGORY = "4_STAR";
    private static final LocalDate VALID_FROM_JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate VALID_FROM_JUN = LocalDate.of(2026, 6, 1);
    private static final BigDecimal AMOUNT_PER_NIGHT = new BigDecimal("2.50");
    private static final BigDecimal HIGHER_AMOUNT_PER_NIGHT = new BigDecimal("3.00");
    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 8, 10);

    @Autowired
    private CityTaxRateRepository cityTaxRateRepository;

    @Autowired
    private HotelCategoryHistoryRepository hotelCategoryHistoryRepository;

    @Autowired
    private AlloggiatiComuneRepository alloggiatiComuneRepository;

    @Autowired
    private HotelSettingsRepository hotelSettingsRepository;

    @BeforeEach
    void seedComune() {
        // city_tax_rates.comune_codice has a real FK to alloggiati_comuni (V17) —
        // a row must exist before any CityTaxRate referencing it can be saved.
        if (alloggiatiComuneRepository.findById(COMUNE_CODICE).isEmpty()) {
            alloggiatiComuneRepository.save(AlloggiatiComune.builder()
                    .codice(COMUNE_CODICE)
                    .descrizione(COMUNE_DESCRIZIONE)
                    .provincia(PROVINCIA_RN)
                    .build());
        }
    }

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void overlappingRateRuleForSameHotelComuneAndCategoryIsRejected() {
        final UUID hotelId = UUID.randomUUID();
        cityTaxRateRepository.saveAndFlush(rate(hotelId, VALID_FROM_JAN, null));

        final CityTaxRate overlapping = rate(hotelId, VALID_FROM_JUN, null);
        assertThrows(DataIntegrityViolationException.class,
                () -> cityTaxRateRepository.saveAndFlush(overlapping));
    }

    @Test
    void adjacentRateRulesWithSharedBoundaryDateAreAccepted() {
        final UUID hotelId = UUID.randomUUID();
        final LocalDate splitDate = VALID_FROM_JUN;
        cityTaxRateRepository.saveAndFlush(rate(hotelId, VALID_FROM_JAN, splitDate));

        final CityTaxRate second = rate(hotelId, splitDate, null);
        cityTaxRateRepository.saveAndFlush(second);

        assertTrue(cityTaxRateRepository.findById(second.getId()).isPresent());
    }

    @Test
    void rateRuleForDifferentCategorySameHotelAndComuneDoesNotOverlap() {
        final UUID hotelId = UUID.randomUUID();
        cityTaxRateRepository.saveAndFlush(rate(hotelId, VALID_FROM_JAN, null));

        final CityTaxRate differentCategory = CityTaxRate.builder()
                .hotelId(hotelId)
                .comuneCodice(COMUNE_CODICE)
                .category("5_STAR")
                .amountPerNight(HIGHER_AMOUNT_PER_NIGHT)
                .validFrom(VALID_FROM_JAN)
                .build();

        assertEquals(differentCategory, cityTaxRateRepository.saveAndFlush(differentCategory));
    }

    @Test
    void findApplicableResolvesTheRuleActiveOnAGivenDate() {
        final UUID hotelId = UUID.randomUUID();
        final LocalDate splitDate = VALID_FROM_JUN;
        cityTaxRateRepository.saveAndFlush(rate(hotelId, VALID_FROM_JAN, splitDate));
        final CityTaxRate current = rate(hotelId, splitDate, null);
        cityTaxRateRepository.saveAndFlush(current);

        final Optional<CityTaxRate> found = cityTaxRateRepository.findApplicableByHotelId(
                hotelId, COMUNE_CODICE, CATEGORY, CHECK_IN_DATE);

        assertTrue(found.isPresent());
        assertEquals(current.getId(), found.get().getId());
    }

    @Test
    void overlappingCategoryHistoryForSameHotelIsRejected() {
        final UUID hotelId = UUID.randomUUID();
        hotelCategoryHistoryRepository.saveAndFlush(categoryHistory(hotelId, VALID_FROM_JAN, null));

        final HotelCategoryHistory overlapping = categoryHistory(hotelId, VALID_FROM_JUN, null);
        assertThrows(DataIntegrityViolationException.class,
                () -> hotelCategoryHistoryRepository.saveAndFlush(overlapping));
    }

    /**
     * Regression test for the flush-ordering bug: {@code CityTaxRateAdminServiceImpl
     * .createRule} used to close the hotel's currently open-ended rule with a plain
     * {@code save()}, which only marks the row dirty — Hibernate's {@code ActionQueue}
     * flushes INSERTs before UPDATEs regardless of call order, so the new rule's INSERT
     * hit the database while the old row still had {@code valid_to IS NULL}, and
     * {@code excl_city_tax_rates_no_overlap} rejected a perfectly legitimate tariff
     * change with a spurious 409. A mocked-repository unit test cannot reproduce this —
     * it needs a real flush against a real GiST exclusion constraint, hence this class.
     */
    @Test
    void createRuleAutoClosesThePreviousOpenEndedRuleWithoutSpuriousConflict() {
        final UUID hotelId = UUID.randomUUID();
        hotelSettingsRepository.saveAndFlush(
                HotelSettings.builder().hotelId(hotelId).comuneCodice(COMUNE_CODICE).build());
        final CityTaxRateAdminServiceImpl service =
                new CityTaxRateAdminServiceImpl(cityTaxRateRepository, hotelSettingsRepository, entity -> null);

        service.createRule(hotelId, new CityTaxRateRequest(
                CATEGORY, AMOUNT_PER_NIGHT, null, null, VALID_FROM_JAN, null));

        service.createRule(hotelId, new CityTaxRateRequest(
                CATEGORY, HIGHER_AMOUNT_PER_NIGHT, null, null, VALID_FROM_JUN, null));

        final CityTaxRate closed = cityTaxRateRepository
                .findApplicableByHotelId(hotelId, COMUNE_CODICE, CATEGORY, VALID_FROM_JAN)
                .orElseThrow();
        assertEquals(VALID_FROM_JUN, closed.getValidTo());
        assertTrue(cityTaxRateRepository
                .findByHotelIdAndComuneCodiceAndCategoryAndValidToIsNull(hotelId, COMUNE_CODICE, CATEGORY)
                .isPresent());
    }

    @Test
    void createRuleWithValidFromNotAfterCurrentRuleIsRejectedInsteadOfViolatingCheckConstraint() {
        final UUID hotelId = UUID.randomUUID();
        hotelSettingsRepository.saveAndFlush(
                HotelSettings.builder().hotelId(hotelId).comuneCodice(COMUNE_CODICE).build());
        final CityTaxRateAdminServiceImpl service =
                new CityTaxRateAdminServiceImpl(cityTaxRateRepository, hotelSettingsRepository, entity -> null);
        service.createRule(hotelId, new CityTaxRateRequest(
                CATEGORY, AMOUNT_PER_NIGHT, null, null, VALID_FROM_JUN, null));

        assertThrows(BadRequestException.class, () -> service.createRule(hotelId, new CityTaxRateRequest(
                CATEGORY, HIGHER_AMOUNT_PER_NIGHT, null, null, VALID_FROM_JAN, null)));
    }

    /** Same flush-ordering regression as above, for {@code HotelCategoryHistoryServiceImpl}. */
    @Test
    void recordCategoryAutoClosesThePreviousOpenEndedEntryWithoutSpuriousConflict() {
        final UUID hotelId = UUID.randomUUID();
        final HotelCategoryHistoryServiceImpl service =
                new HotelCategoryHistoryServiceImpl(hotelCategoryHistoryRepository, entity -> null);

        service.recordCategory(hotelId, new HotelCategoryHistoryRequest("3_STAR", VALID_FROM_JAN));
        service.recordCategory(hotelId, new HotelCategoryHistoryRequest(CATEGORY, VALID_FROM_JUN));

        final HotelCategoryHistory closed = hotelCategoryHistoryRepository
                .findApplicableByHotelId(hotelId, VALID_FROM_JAN)
                .orElseThrow();
        assertEquals(VALID_FROM_JUN, closed.getValidTo());
        assertTrue(hotelCategoryHistoryRepository.findByHotelIdAndValidToIsNull(hotelId).isPresent());
    }

    private static CityTaxRate rate(final UUID hotelId, final LocalDate validFrom, final LocalDate validTo) {
        return CityTaxRate.builder()
                .hotelId(hotelId)
                .comuneCodice(COMUNE_CODICE)
                .category(CATEGORY)
                .amountPerNight(AMOUNT_PER_NIGHT)
                .validFrom(validFrom)
                .validTo(validTo)
                .build();
    }

    private static HotelCategoryHistory categoryHistory(
            final UUID hotelId, final LocalDate validFrom, final LocalDate validTo) {
        return HotelCategoryHistory.builder()
                .hotelId(hotelId)
                .category(CATEGORY)
                .validFrom(validFrom)
                .validTo(validTo)
                .build();
    }
}
