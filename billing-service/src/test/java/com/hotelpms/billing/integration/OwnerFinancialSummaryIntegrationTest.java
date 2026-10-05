package com.hotelpms.billing.integration;

import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.domain.Payment;
import com.hotelpms.billing.domain.PaymentMethod;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.repository.OwnerFinancialSummaryAggregates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the Dashboard/Billing summary aggregate against a real PostgreSQL: the unit tests
 * mock the repository and cannot see the JPQL. Defines "fatturato" (everything issued in
 * the period except CANCELLED invoices) and "da incassare" (what ISSUED invoices still owe
 * after the payments received so far — a partial payment leaves the invoice ISSUED).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true"
})
class OwnerFinancialSummaryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_billing_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final int DAYS_BEFORE_PERIOD = 90;
    private static final int PAID_TOTAL = 100;
    private static final int PARTLY_PAID_TOTAL = 200;
    private static final int PARTIAL_PAYMENT = 50;
    private static final int OPEN_TOTAL = 300;
    private static final int CANCELLED_TOTAL = 999;
    private static final int OUT_OF_PERIOD_TOTAL = 1000;
    private static final int OTHER_HOTEL_TOTAL = 5000;
    private static final int SOFT_DELETED_TOTAL = 400;
    private static final int SOFT_DELETED_PAYMENT = 100;
    private static final int BILLED_COUNT = 3;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TestEntityManager entityManager;

    private int sequence;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    @DisplayName("revenue and invoice count skip CANCELLED invoices; pending is the unpaid balance of ISSUED ones")
    void aggregatesExcludeCancelledAndSubtractPartialPayments() {
        final LocalDateTime now = LocalDateTime.now();
        final Invoice paid = seedInvoice(HOTEL_ID, PAID_TOTAL, InvoiceStatus.PAID, now);
        addPayment(paid, PAID_TOTAL, true);
        final Invoice partlyPaid = seedInvoice(HOTEL_ID, PARTLY_PAID_TOTAL, InvoiceStatus.ISSUED, now);
        addPayment(partlyPaid, PARTIAL_PAYMENT, true);
        seedInvoice(HOTEL_ID, OPEN_TOTAL, InvoiceStatus.ISSUED, now);
        seedInvoice(HOTEL_ID, CANCELLED_TOTAL, InvoiceStatus.CANCELLED, now);
        entityManager.flush();
        entityManager.clear();

        final OwnerFinancialSummaryAggregates result = summaryAroundNow();

        assertMoney(PAID_TOTAL + PARTLY_PAID_TOTAL + OPEN_TOTAL, result.getTotalRevenue(), "no cancelled invoice");
        assertEquals(BILLED_COUNT, result.getTotalInvoices());
        assertEquals(1, result.getPaidInvoices());
        assertMoney(PARTLY_PAID_TOTAL - PARTIAL_PAYMENT + OPEN_TOTAL, result.getPendingRevenue(),
                "the unpaid balance of both ISSUED invoices");
    }

    @Test
    @DisplayName("ignores invoices outside the period and invoices of another hotel")
    void scopedToPeriodAndHotel() {
        final LocalDateTime now = LocalDateTime.now();
        seedInvoice(HOTEL_ID, PAID_TOTAL, InvoiceStatus.ISSUED, now);
        seedInvoice(HOTEL_ID, OUT_OF_PERIOD_TOTAL, InvoiceStatus.ISSUED, now.minusDays(DAYS_BEFORE_PERIOD));
        seedInvoice(OTHER_HOTEL_ID, OTHER_HOTEL_TOTAL, InvoiceStatus.ISSUED, now);
        entityManager.flush();
        entityManager.clear();

        final OwnerFinancialSummaryAggregates result = summaryAroundNow();

        assertMoney(PAID_TOTAL, result.getTotalRevenue());
        assertEquals(1, result.getTotalInvoices());
        assertMoney(PAID_TOTAL, result.getPendingRevenue());
    }

    @Test
    @DisplayName("a soft-deleted payment does not reduce what is still owed")
    void softDeletedPaymentsAreNotCounted() {
        final Invoice invoice = seedInvoice(HOTEL_ID, SOFT_DELETED_TOTAL, InvoiceStatus.ISSUED, LocalDateTime.now());
        addPayment(invoice, SOFT_DELETED_PAYMENT, false);
        entityManager.flush();
        entityManager.clear();

        assertMoney(SOFT_DELETED_TOTAL, summaryAroundNow().getPendingRevenue());
    }

    @Test
    @DisplayName("an empty period reports zeros, not nulls")
    void emptyPeriodReportsZeros() {
        final OwnerFinancialSummaryAggregates result = summaryAroundNow();

        assertMoney(0, result.getTotalRevenue());
        assertEquals(0, result.getTotalInvoices());
        assertEquals(0, result.getPaidInvoices());
        assertMoney(0, result.getPendingRevenue());
    }

    private static void assertMoney(final int expected, final BigDecimal actual) {
        assertMoney(expected, actual, null);
    }

    private static void assertMoney(final int expected, final BigDecimal actual, final String message) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual), message);
    }

    private OwnerFinancialSummaryAggregates summaryAroundNow() {
        final LocalDateTime now = LocalDateTime.now();
        return invoiceRepository.getFinancialSummaryAggregatesByHotelId(
                HOTEL_ID, now.minusDays(1), now.plusDays(1));
    }

    private Invoice seedInvoice(final UUID hotelId, final int total, final InvoiceStatus status,
                                final LocalDateTime issueDate) {
        sequence++;
        return entityManager.persist(Invoice.builder()
                .hotelId(hotelId)
                .invoiceNumber("TEST-" + hotelId.getLeastSignificantBits() + "-" + sequence)
                .issueDate(issueDate)
                .totalAmount(BigDecimal.valueOf(total))
                .status(status)
                .guestId(UUID.randomUUID())
                .reservationId(UUID.randomUUID())
                .build());
    }

    private void addPayment(final Invoice invoice, final int amount, final boolean active) {
        final Payment payment = Payment.builder()
                .paymentDate(LocalDateTime.now())
                .amount(BigDecimal.valueOf(amount))
                .paymentMethod(PaymentMethod.CASH)
                .active(active)
                .build();
        invoice.addPayment(payment);
        entityManager.persist(payment);
    }
}
