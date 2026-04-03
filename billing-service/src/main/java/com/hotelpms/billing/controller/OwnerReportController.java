package com.hotelpms.billing.controller;

import com.hotelpms.billing.dto.OwnerFinancialReportDto;
import com.hotelpms.billing.service.OwnerReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST Controller for Owner-only financial reporting.
 * Secured to OWNER and ADMIN roles via Spring Security method-level
 * authorization.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
public class OwnerReportController {

    private final OwnerReportService ownerReportService;

    /**
     * Returns an aggregated financial report for the given date range.
     * Access is restricted to users with the OWNER or ADMIN role.
     *
     * @param startDate the start of the period (inclusive), format YYYY-MM-DD
     * @param endDate   the end of the period (inclusive), format YYYY-MM-DD
     * @return the aggregated financial report
     */
    @GetMapping("/owner")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<OwnerFinancialReportDto> getOwnerFinancialReport(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate startDate,
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate endDate) {
        log.info("REST request for owner financial report from {} to {}", startDate, endDate);
        final OwnerFinancialReportDto report = ownerReportService.getFinancialReport(startDate, endDate);
        return ResponseEntity.ok(report);
    }
}
