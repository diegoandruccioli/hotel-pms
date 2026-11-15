package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.quotations.domain.Quotation;
import com.hotelpms.frontdesk.quotations.domain.QuotationStatus;
import com.hotelpms.frontdesk.quotations.repository.QuotationRepository;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link QuotationRepository#searchByHotelId} against a real PostgreSQL database: the query mixes
 * {@code NULL}-skippable date bounds, a status set, a free-text {@code LIKE} over nullable prospect
 * columns and an {@code IN} over guest ids, and the soft-delete and tenant scopes — a mocked
 * repository proves none of it.
 *
 * <p>The service turns {@code EXPIRED} (never stored) into {@code DRAFT}/{@code SENT} plus a
 * {@code validBefore} bound, and a live {@code DRAFT}/{@code SENT} filter into {@code validFrom};
 * this checks the rows those bounds select, including the day the quotation is valid until.
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
class QuotationSearchRepositoryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final BigDecimal TOTAL = new BigDecimal("300.00");
    private static final int PAGE_SIZE = 50;
    // The stay the quotations are for; irrelevant to the search, but the columns are not nullable.
    private static final int STAY_START_DAYS = 20;
    private static final int STAY_END_DAYS = 23;
    private static final Set<QuotationStatus> ANY_STATUS = EnumSet.allOf(QuotationStatus.class);
    private static final String BIANCHI = "bianchi";

    @Autowired
    private QuotationRepository quotationRepository;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private Seed seed() {
        final UUID hotelId = UUID.randomUUID();
        final UUID rossi = UUID.randomUUID();
        final UUID verdi = UUID.randomUUID();
        final Seed seed = new Seed(
                hotelId, rossi, verdi,
                save(hotelId, rossi, QuotationStatus.DRAFT, TODAY.plusDays(3), true),
                save(hotelId, rossi, QuotationStatus.DRAFT, TODAY, true),
                save(hotelId, rossi, QuotationStatus.DRAFT, TODAY.minusDays(1), true),
                save(hotelId, verdi, QuotationStatus.SENT, TODAY.plusDays(1), true),
                save(hotelId, verdi, QuotationStatus.SENT, TODAY.minusDays(2), true),
                // A past validity date does not make an accepted or declined quotation expired.
                save(hotelId, verdi, QuotationStatus.ACCEPTED, TODAY.minusDays(30), true),
                save(hotelId, verdi, QuotationStatus.DECLINED, TODAY.minusDays(30), true),
                saveProspect(hotelId));
        // Must never match: another hotel's, and a soft-deleted one of this hotel.
        save(UUID.randomUUID(), rossi, QuotationStatus.DRAFT, TODAY.plusDays(3), true);
        save(hotelId, rossi, QuotationStatus.DRAFT, TODAY.plusDays(3), false);
        return seed;
    }

    private Quotation save(
            final UUID hotelId, final UUID guestId, final QuotationStatus status, final LocalDate validUntil,
            final boolean active) {
        return quotationRepository.saveAndFlush(Quotation.builder()
                .hotelId(hotelId).guestId(guestId)
                .checkInDate(TODAY.plusDays(STAY_START_DAYS)).checkOutDate(TODAY.plusDays(STAY_END_DAYS))
                .status(status).validUntil(validUntil).totalPrice(TOTAL).active(active)
                .build());
    }

    private Quotation saveProspect(final UUID hotelId) {
        return quotationRepository.saveAndFlush(Quotation.builder()
                .hotelId(hotelId)
                .prospectFirstName("Anna").prospectLastName("Bianchi").prospectEmail("Anna.Bianchi@example.com")
                .checkInDate(TODAY.plusDays(STAY_START_DAYS)).checkOutDate(TODAY.plusDays(STAY_END_DAYS))
                .status(QuotationStatus.DRAFT).validUntil(TODAY.plusDays(2)).totalPrice(TOTAL)
                .build());
    }

    private Set<UUID> search(
            final Seed seed, final Set<QuotationStatus> statuses, final LocalDate validFrom,
            final LocalDate validBefore, final String query, final List<UUID> guestIds) {
        final Pageable pageable = PageRequest.of(0, PAGE_SIZE);
        return quotationRepository
                .searchByHotelId(seed.hotelId(), statuses, validFrom, validBefore, query, guestIds, pageable)
                .stream().map(Quotation::getId).collect(Collectors.toSet());
    }

    private static Set<UUID> ids(final Quotation... quotations) {
        return java.util.Arrays.stream(quotations).map(Quotation::getId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("With no filter it returns every active quotation of the hotel and nothing else")
    void noFilterReturnsEveryActiveQuotationOfTheHotel() {
        final Seed s = seed();

        assertEquals(
                ids(s.liveDraft(), s.dueToday(), s.expiredDraft(), s.sent(), s.expiredSent(), s.accepted(),
                        s.declined(), s.prospect()),
                search(s, ANY_STATUS, null, null, null, List.of()));
    }

    @Test
    @DisplayName("A live draft or sent filter keeps the quotation valid until today and leaves out the past-due ones")
    void aLiveDraftFilterLeavesOutThePastDueOnes() {
        final Seed s = seed();

        assertEquals(ids(s.liveDraft(), s.dueToday(), s.prospect()),
                search(s, EnumSet.of(QuotationStatus.DRAFT), TODAY, null, null, List.of()));
        assertEquals(ids(s.sent()),
                search(s, EnumSet.of(QuotationStatus.SENT), TODAY, null, null, List.of()));
    }

    @Test
    @DisplayName("Expired selects draft and sent quotations valid until a day before today, never the day itself")
    void expiredSelectsOnlyDraftAndSentBeforeToday() {
        final Seed s = seed();

        assertEquals(ids(s.expiredDraft(), s.expiredSent()),
                search(s, EnumSet.of(QuotationStatus.DRAFT, QuotationStatus.SENT), null, TODAY, null, List.of()));
    }

    @Test
    @DisplayName("Accepted and declined are matched by status alone, whatever their validity date")
    void terminalStatusesIgnoreTheValidityDate() {
        final Seed s = seed();

        assertEquals(ids(s.accepted()),
                search(s, EnumSet.of(QuotationStatus.ACCEPTED), null, null, null, List.of()));
        assertEquals(ids(s.declined()),
                search(s, EnumSet.of(QuotationStatus.DECLINED), null, null, null, List.of()));
    }

    @Test
    @DisplayName("The query finds a prospect by name or email, whatever the case, and returns nothing for a stranger")
    void theQueryMatchesAProspectByNameOrEmail() {
        final Seed s = seed();

        assertEquals(ids(s.prospect()), search(s, ANY_STATUS, null, null, BIANCHI, List.of()));
        assertEquals(ids(s.prospect()), search(s, ANY_STATUS, null, null, "anna bia", List.of()));
        assertEquals(ids(s.prospect()), search(s, ANY_STATUS, null, null, "ANNA.BIANCHI@EXAMPLE", List.of()));
        assertEquals(Set.of(), search(s, ANY_STATUS, null, null, "zzz", List.of()));
    }

    @Test
    @DisplayName("The query finds a registered guest's quotations through the guest ids guest-service resolved")
    void theQueryMatchesRegisteredGuestsThroughTheirIds() {
        final Seed s = seed();

        assertEquals(ids(s.sent(), s.expiredSent(), s.accepted(), s.declined()),
                search(s, ANY_STATUS, null, null, "verdi", List.of(s.guestVerdi())));
    }

    @Test
    @DisplayName("A guest match and a prospect match are alternatives: both are returned")
    void guestAndProspectMatchesAreAlternatives() {
        final Seed s = seed();

        assertEquals(
                ids(s.liveDraft(), s.dueToday(), s.expiredDraft(), s.prospect()),
                search(s, ANY_STATUS, null, null, BIANCHI, List.of(s.guestRossi())));
    }

    @Test
    @DisplayName("Status, date bound and query narrow each other")
    void statusDateAndQueryCombine() {
        final Seed s = seed();

        assertEquals(ids(s.expiredDraft()),
                search(s, EnumSet.of(QuotationStatus.DRAFT, QuotationStatus.SENT), null, TODAY, "rossi",
                        List.of(s.guestRossi())));
    }

    /**
     * One hotel's quotations: every state the filters must tell apart. Two more are saved that must
     * never match (another hotel's, and a soft-deleted one) and are not kept here.
     *
     * @param hotelId      the hotel the quotations belong to
     * @param guestRossi   a registered guest with the draft quotations
     * @param guestVerdi   a registered guest with the sent, accepted and declined quotations
     * @param liveDraft    a draft valid for three more days
     * @param dueToday     a draft valid until today: still valid
     * @param expiredDraft a draft valid until yesterday
     * @param sent         a sent quotation still valid
     * @param expiredSent  a sent quotation past its validity date
     * @param accepted     an accepted quotation with a long-past validity date
     * @param declined     a declined quotation with a long-past validity date
     * @param prospect     a quotation for a prospect with no guest id
     */
    private record Seed(
            UUID hotelId, UUID guestRossi, UUID guestVerdi,
            Quotation liveDraft, Quotation dueToday, Quotation expiredDraft, Quotation sent, Quotation expiredSent,
            Quotation accepted, Quotation declined, Quotation prospect) {
    }
}
