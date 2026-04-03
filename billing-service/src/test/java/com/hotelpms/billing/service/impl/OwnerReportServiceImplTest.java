package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.OwnerFinancialReportDto;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerReportServiceImplTest {

        private static final int YEAR = 2026;
        private static final int MONTH_START = 1;
        private static final int DAY_START = 1;
        private static final int MONTH_END = 12;
        private static final int DAY_END = 31;
        private static final int MONTH_MAR = 3;
        private static final int DAY_MAR_1 = 1;
        private static final int DAY_MAR_2 = 2;
        private static final int DAY_MAR_3 = 3;
        private static final int HOUR_12 = 12;
        private static final int HOUR_10 = 10;
        private static final int HOUR_9 = 9;

        @Mock
        private InvoiceRepository invoiceRepository;

        @Mock
        private InvoiceMapper invoiceMapper;

        @InjectMocks
        private OwnerReportServiceImpl ownerReportService;

        private Invoice paidInvoice1;
        private Invoice paidInvoice2;
        private Invoice issuedInvoice;
        private LocalDate startDate;
        private LocalDate endDate;

        @BeforeEach
        void setUp() {
                startDate = LocalDate.of(YEAR, MONTH_START, DAY_START);
                endDate = LocalDate.of(YEAR, MONTH_END, DAY_END);

                paidInvoice1 = Invoice.builder()
                                .id(UUID.randomUUID())
                                .invoiceNumber("INV-0001")
                                .totalAmount(new BigDecimal("300.00"))
                                .status(InvoiceStatus.PAID)
                                .issueDate(LocalDateTime.of(YEAR, MONTH_MAR, DAY_MAR_1, HOUR_12, 0))
                                .guestId(UUID.randomUUID())
                                .reservationId(UUID.randomUUID())
                                .build();

                paidInvoice2 = Invoice.builder()
                                .id(UUID.randomUUID())
                                .invoiceNumber("INV-0002")
                                .totalAmount(new BigDecimal("450.50"))
                                .status(InvoiceStatus.PAID)
                                .issueDate(LocalDateTime.of(YEAR, MONTH_MAR, DAY_MAR_2, HOUR_10, 0))
                                .guestId(UUID.randomUUID())
                                .reservationId(UUID.randomUUID())
                                .build();

                issuedInvoice = Invoice.builder()
                                .id(UUID.randomUUID())
                                .invoiceNumber("INV-0003")
                                .totalAmount(new BigDecimal("120.00"))
                                .status(InvoiceStatus.ISSUED)
                                .issueDate(LocalDateTime.of(YEAR, MONTH_MAR, DAY_MAR_3, HOUR_9, 0))
                                .guestId(UUID.randomUUID())
                                .reservationId(UUID.randomUUID())
                                .build();
        }

        @Test
        void shouldReturnCorrectAggregatesForThreeInvoicesTwoPaid() {
                // Arrange
                when(invoiceRepository.findByIssueDateBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of(paidInvoice1, paidInvoice2, issuedInvoice));
                when(invoiceMapper.toResponse(any(Invoice.class))).thenReturn(mock(InvoiceResponse.class));

                // Act
                final OwnerFinancialReportDto report = ownerReportService.getFinancialReport(startDate, endDate);

                // Assert
                assertNotNull(report);
                assertEquals(3, report.totalInvoices(), "Total invoice count should be 3");
                assertEquals(2, report.paidInvoices(), "Paid invoice count should be 2");
                assertEquals(new BigDecimal("870.50"), report.totalRevenue(),
                                "Total revenue should be sum of all invoice amounts");
                assertEquals(3, report.invoices().size(), "Invoice list should contain 3 entries");
                assertEquals(startDate, report.startDate());
                assertEquals(endDate, report.endDate());
        }

        @Test
        void shouldReturnZeroRevenueWhenNoInvoicesFound() {
                // Arrange
                when(invoiceRepository.findByIssueDateBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                                .thenReturn(List.of());

                // Act
                final OwnerFinancialReportDto report = ownerReportService.getFinancialReport(startDate, endDate);

                // Assert
                assertNotNull(report);
                assertEquals(0, report.totalInvoices());
                assertEquals(0, report.paidInvoices());
                assertEquals(BigDecimal.ZERO, report.totalRevenue());
        }
}
