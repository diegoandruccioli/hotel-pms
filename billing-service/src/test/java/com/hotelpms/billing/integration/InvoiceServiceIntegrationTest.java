package com.hotelpms.billing.integration;

import com.hotelpms.billing.domain.DocumentType;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.SdiStatus;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.StayInvoiceRequest;
import com.hotelpms.billing.mapper.InvoiceChargeMapperImpl;
import com.hotelpms.billing.mapper.InvoiceMapperImpl;
import com.hotelpms.billing.mapper.PaymentMapperImpl;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.impl.InvoiceServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for InvoiceService using the JPA test slice.
 *
 * <p>{@code @DataJpaTest} loads only JPA/Flyway/transaction infrastructure — no Feign,
 * no Security filter chain, no Redis — so no mocks are needed for those concerns.
 * Flyway applies all V1–V9 migrations at context startup; each test rolls back
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
        InvoiceServiceIntegrationTest.AuditingTestConfig.class,
        InvoiceServiceImpl.class,
        InvoiceMapperImpl.class,
        InvoiceChargeMapperImpl.class,
        PaymentMapperImpl.class
})
@SuppressWarnings("null")
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

    /**
     * Enables JPA auditing for the test slice context.
     * {@code BillingApplication} carries {@code @EnableJpaAuditing} in production;
     * {@code @DataJpaTest} does not load the main class, so this config restores it.
     */
    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingTestConfig {
    }
}
