package com.hotelpms.billing.integration;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.domain.ChargeType;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceCharge;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.mapper.InvoiceChargeMapperImpl;
import com.hotelpms.billing.mapper.InvoiceMapperImpl;
import com.hotelpms.billing.mapper.PaymentMapperImpl;
import com.hotelpms.billing.repository.InvoiceChargeRepository;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.repository.RoomRevenuePeriod;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.impl.InvoiceServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Regression test for the KPI trend report's native query (epic C4). The original
 * version bound {@code :granularity} twice — once in the {@code SELECT}'s
 * {@code date_trunc(:granularity, ...)} and again in an identical {@code GROUP BY}
 * expression — which PostgreSQL rejects (42803: "column must appear in the GROUP BY
 * clause") because two separate bind-parameter occurrences of the same expression are
 * not recognized as equal for grouping-validity purposes, even though both resolve to
 * the same runtime value. {@code KpiReportServiceImplTest} mocks the repository and
 * cannot see this class of bug — only a real Postgres, via Testcontainers here, can.
 *
 * <p>Also covers the per-night revenue distribution rewrite (live QA, 2026-09): a
 * {@code ROOM_NIGHT} charge is posted once at check-in for the whole stay length, so
 * the query must spread its {@code amount} across the {@code nights} calendar nights
 * it actually covers rather than crediting all of it to the single bucket containing
 * {@code issue_date} (the check-in day) — see
 * {@code distributesMultiNightChargeAcrossItsActualNights} below, which only a real
 * Postgres running {@code generate_series} can verify.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true"
})
@Import({
        InvoiceServiceImpl.class,
        InvoiceMapperImpl.class,
        InvoiceChargeMapperImpl.class,
        PaymentMapperImpl.class
})
class KpiReportGranularityIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_billing_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final UUID HOTEL_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BigDecimal ROOM_CHARGE_AMOUNT = new BigDecimal("125.00");

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceChargeRepository invoiceChargeRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @MockitoBean
    private GuestClient guestClient;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void setUpSecurityContext() {
        final UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("admin", "", List.of());
        auth.setDetails(HOTEL_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "granularity={0}")
    @ValueSource(strings = {"day", "week", "month"})
    @DisplayName("sumRoomRevenueByHotelIdGroupedByPeriod does not throw 42803 for any granularity")
    void doesNotThrowForAnyGranularity(final String granularity) {
        seedOneRoomNightCharge();

        final LocalDateTime start = LocalDateTime.now().minusDays(60);
        final LocalDateTime end = LocalDateTime.now().plusDays(1);

        assertDoesNotThrow(() -> invoiceChargeRepository.sumRoomRevenueByHotelIdGroupedByPeriod(
                HOTEL_ID, start, end, granularity));
    }

    @Test
    @DisplayName("the bucket containing today's charge reports the real charge amount, not zero/missing")
    void reportsCorrectRevenueForTodaysBucket() {
        seedOneRoomNightCharge();

        final LocalDateTime start = LocalDateTime.now().minusDays(1);
        final LocalDateTime end = LocalDateTime.now().plusDays(1);

        final List<RoomRevenuePeriod> periods = invoiceChargeRepository
                .sumRoomRevenueByHotelIdGroupedByPeriod(HOTEL_ID, start, end, "day");

        assertTrue(periods.stream().anyMatch(p -> p.getTotalRevenue().compareTo(ROOM_CHARGE_AMOUNT) == 0),
                "expected a bucket totalling " + ROOM_CHARGE_AMOUNT + ", got: " + periods);
    }

    @Test
    @DisplayName("distributes a multi-night charge's revenue across the individual nights it covers, "
            + "not just the single issue-date bucket")
    void distributesMultiNightChargeAcrossItsActualNights() {
        // A ROOM_NIGHT charge is posted once, at check-in — issueDate is the check-in
        // instant, `nights` covers forward from it. Issuing 2 days ago for a 3-night
        // stay means tonight and tomorrow night are covered too, not just 2 days ago.
        final int nights = 3;
        final BigDecimal totalAmount = new BigDecimal("270.00");
        final LocalDateTime issueDate = LocalDateTime.now().minusDays(2);
        seedRoomNightCharge(totalAmount, nights, issueDate);

        final LocalDateTime start = LocalDateTime.now().minusDays(10);
        final LocalDateTime end = LocalDateTime.now().plusDays(10);

        final List<RoomRevenuePeriod> periods = invoiceChargeRepository
                .sumRoomRevenueByHotelIdGroupedByPeriod(HOTEL_ID, start, end, "day");

        final long nonZeroBuckets = periods.stream()
                .filter(p -> p.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0)
                .count();
        assertEquals(nights, nonZeroBuckets,
                "expected exactly " + nights + " non-zero day buckets (one per covered night), got: " + periods);

        final BigDecimal summedBack = periods.stream()
                .map(RoomRevenuePeriod::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalAmount.compareTo(summedBack),
                "the per-night amounts should sum back to the full charge, got: " + summedBack);
    }

    private void seedOneRoomNightCharge() {
        seedRoomNightCharge(ROOM_CHARGE_AMOUNT, null, null);
    }

    /**
     * Persists an invoice (via the real service, so ID/invoice-number generation and
     * defaults stay realistic) plus one ROOM_NIGHT charge on it.
     *
     * @param amount    the charge's total amount, covering every night it spans
     * @param nights    how many nights forward from {@code issueDate} the charge covers,
     *                  or {@code null} to leave it unset (defaults to a single night at
     *                  the query layer)
     * @param issueDate overrides the invoice's issue date (normally "now", set by
     *                  {@code invoiceService.createInvoiceForStay}) to backdate the
     *                  charge's covered nights for the test, or {@code null} to leave
     *                  the service-assigned "now" timestamp untouched
     */
    private void seedRoomNightCharge(final BigDecimal amount, final Integer nights, final LocalDateTime issueDate) {
        final InvoiceResponse invoice = invoiceService.createInvoiceForStay(
                new StayInvoiceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        final Invoice invoiceEntity = invoiceRepository.findById(invoice.id()).orElseThrow();
        if (issueDate != null) {
            invoiceEntity.setIssueDate(issueDate);
        }

        final InvoiceCharge charge = InvoiceCharge.builder()
                .invoice(invoiceEntity)
                .type(ChargeType.ROOM_NIGHT)
                .description("Room charge — KPI regression test")
                .amount(amount)
                .nights(nights)
                .vatRate(new BigDecimal("0.10"))
                .build();
        testEntityManager.persist(charge);
        testEntityManager.flush();
    }
}
