package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.domain.Invoice;
import com.hotelpms.billing.domain.InvoiceStatus;
import com.hotelpms.billing.dto.InvoiceResponse;
import com.hotelpms.billing.dto.OwnerFinancialReportDto;
import com.hotelpms.billing.mapper.InvoiceMapper;
import com.hotelpms.billing.repository.InvoiceRepository;
import com.hotelpms.billing.service.OwnerReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of the OwnerReportService for financial reporting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerReportServiceImpl implements OwnerReportService {

        private final InvoiceRepository invoiceRepository;
        private final InvoiceMapper invoiceMapper;

        /** {@inheritDoc} */
        @Override
        @Transactional(readOnly = true)
        public OwnerFinancialReportDto getFinancialReport(final LocalDate startDate, final LocalDate endDate) {
                log.info("Generating owner financial report from {} to {}", startDate, endDate);

                final LocalDateTime start = startDate.atStartOfDay();
                final LocalDateTime end = endDate.plusDays(1).atStartOfDay();

                final List<Invoice> invoices = invoiceRepository.findByIssueDateBetween(start, end);

                final BigDecimal totalRevenue = invoices.stream()
                                .map(Invoice::getTotalAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                final long totalInvoices = invoices.size();
                final long paidInvoices = invoices.stream()
                                .filter(inv -> inv.getStatus() == InvoiceStatus.PAID)
                                .count();

                final List<InvoiceResponse> invoiceResponses = invoices.stream()
                                .map(invoiceMapper::toResponse)
                                .toList();

                log.info("Report: {} invoices, {} paid, total revenue {}", totalInvoices, paidInvoices, totalRevenue);

                return new OwnerFinancialReportDto(startDate, endDate, totalRevenue, totalInvoices, paidInvoices,
                                invoiceResponses);
        }
}
