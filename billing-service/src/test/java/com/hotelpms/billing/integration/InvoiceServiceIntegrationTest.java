package com.hotelpms.billing.integration;

import com.hotelpms.billing.client.GuestClient;
import com.hotelpms.billing.client.dto.GuestSearchPageResponse;
import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.SdiStatus;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.InvoiceSearchResultResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.mapper.InvoiceChargeMapperImpl;
import com.hotelpms.billing.mapper.InvoiceMapperImpl;
import com.hotelpms.billing.mapper.PaymentMapperImpl;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.impl.InvoiceServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Integration tests for InvoiceService using the JPA test slice.
 *
 * <p>{@code @DataJpaTest} loads only JPA/Flyway/transaction infrastructure — no real
 * Feign client wiring, no Security filter chain, no Redis. {@code InvoiceServiceImpl}
 * now takes {@link GuestClient} as a constructor dependency (C12 invoice search); since
 * this slice doesn't configure OpenFeign, it's provided as a {@code @MockitoBean} purely
 * so the context can wire the constructor — stubbed only in the {@code searchInvoices}
 * tests below, which are the only ones that call it.
 * {@code BillingApplication}'s {@code @EnableJpaAuditing} is processed by the slice
 * bootstrapper, so {@code Invoice}'s {@code @CreatedDate}/{@code @LastModifiedDate} fields
 * are populated. Flyway applies all migrations at context startup; each test rolls back
 * via the implicit {@code @Transactional} provided by {@code @DataJpaTest}.
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
class InvoiceServiceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_billing_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final UUID HOTEL_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

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

    @Test
    @DisplayName("Context loads — all Flyway migrations apply without error")
    void contextLoadsAndFlywayMigrationsApplied() {
        assertNotNull(invoiceService, "InvoiceService must be present in application context");
    }

    @Test
    @DisplayName("First invoice for a hotel in a year gets number YYYY/0001")
    void firstInvoiceGetsSequentialNumber0001() {
        final int currentYear = LocalDate.now().getYear();
        final StayInvoiceRequest request =
                new StayInvoiceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        final InvoiceResponse response = invoiceService.createInvoiceForStay(request);

        assertEquals(currentYear + "/0001", response.invoiceNumber());
    }

    @Test
    @DisplayName("Second invoice in the same year for the same hotel gets YYYY/0002")
    void secondInvoiceGetsSequentialNumber0002() {
        final int currentYear = LocalDate.now().getYear();
        invoiceService.createInvoiceForStay(
                new StayInvoiceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        final InvoiceResponse second = invoiceService.createInvoiceForStay(
                new StayInvoiceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(currentYear + "/0002", second.invoiceNumber());
    }

    @Test
    @DisplayName("Created invoice defaults to ISSUED / FATTURA / NOT_SENT")
    void createdInvoiceHasCorrectDefaultState() {
        final InvoiceResponse response = invoiceService.createInvoiceForStay(
                new StayInvoiceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(InvoiceStatus.ISSUED, response.status());
        assertEquals(DocumentType.FATTURA, response.documentType());
        assertEquals(SdiStatus.NOT_SENT, response.sdiStatus());
    }

    @Test
    @DisplayName("Invoice number follows YYYY/NNNN format with zero-padded four-digit suffix")
    void invoiceNumberFollowsExpectedFormat() {
        final int currentYear = LocalDate.now().getYear();
        final InvoiceResponse response = invoiceService.createInvoiceForStay(
                new StayInvoiceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        final String number = response.invoiceNumber();
        assertNotNull(number);
        assertTrue(number.startsWith(currentYear + "/"),
                () -> "Expected prefix " + currentYear + "/ but got: " + number);
        final String suffix = number.substring(number.indexOf('/') + 1);
        assertEquals(4, suffix.length(), "Suffix must be exactly 4 digits (zero-padded)");
    }

    @Test
    @DisplayName("P1: saving a stale Invoice (concurrent modification) throws ObjectOptimisticLockingFailureException")
    void concurrentInvoiceUpdateThrowsOptimisticLockingException() {
        final InvoiceResponse created = invoiceService.createInvoiceForStay(
                new StayInvoiceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        testEntityManager.flush();
        testEntityManager.clear();

        // Load and keep a managed reference — its in-memory @Version stays at 0
        // even after the row is bumped from under it below (Hibernate never
        // silently refreshes an already-loaded entity's version).
        final Invoice loaded = invoiceRepository.findById(created.id()).orElseThrow();

        // Simulate a concurrent writer (e.g. F&B service adding a charge to the
        // same invoice) committing out-of-band, via a native UPDATE that bypasses
        // this entity manager's persistence context entirely.
        testEntityManager.getEntityManager()
                .createNativeQuery("UPDATE invoices SET version = version + 1 WHERE id = :id")
                .setParameter("id", created.id())
                .executeUpdate();
        testEntityManager.flush();

        loaded.setStatus(InvoiceStatus.CANCELLED);
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> invoiceRepository.saveAndFlush(loaded),
                "The stale reader must lose the race: version in memory (0) no longer "
                        + "matches the row's real version (bumped concurrently to 1)");
    }

    @Test
    @DisplayName("BUG-0: searchInvoices with no free-text query succeeds against real Postgres "
            + "(regression guard for the lower(bytea) parameter-type inference bug)")
    void searchInvoicesWithoutQuerySucceeds() {
        invoiceService.createInvoiceForStay(
                new StayInvoiceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        final Page<InvoiceSearchResultResponse> results = invoiceService.searchInvoices(
                null, null, null, null, PageRequest.of(0, 20));

        assertTrue(results.getTotalElements() >= 1);
    }

    @Test
    @DisplayName("BUG-0: searchInvoices with a free-text query filters by invoice number "
            + "and does not leak results excluded by status/date filters (parenthesis grouping bug)")
    void searchInvoicesWithQueryFiltersByInvoiceNumberOnly() {
        final InvoiceResponse created = invoiceService.createInvoiceForStay(
                new StayInvoiceRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        when(guestClient.searchGuests(any(), anyInt()))
                .thenReturn(new GuestSearchPageResponse(List.of()));

        final Page<InvoiceSearchResultResponse> matching = invoiceService.searchInvoices(
                null, created.invoiceNumber(), null, null, PageRequest.of(0, 20));
        assertEquals(1, matching.getTotalElements());
        assertEquals(created.invoiceNumber(),
                matching.getContent().get(0).invoice().invoiceNumber());

        final Page<InvoiceSearchResultResponse> statusExcluded = invoiceService.searchInvoices(
                InvoiceStatus.CANCELLED, created.invoiceNumber(), null, null, PageRequest.of(0, 20));
        assertEquals(0, statusExcluded.getTotalElements(),
                "A status filter must still apply even when the free-text query matches — "
                        + "guestId matches must not bypass status/date filters");
    }

}
