package com.hotelpms.billing.controller;

import com.hotelpms.billing.dto.KpiReportDto;
import com.hotelpms.billing.dto.OwnerFinancialReportDto;
import com.hotelpms.billing.dto.OwnerFinancialSummaryDto;
import com.hotelpms.billing.dto.ReportGranularity;
import com.hotelpms.billing.service.KpiReportService;
import com.hotelpms.billing.service.OwnerReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

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
    private final KpiReportService kpiReportService;

    /**
     * Returns an aggregated financial report for the given date range, scoped to
     * the caller's hotel (T-BILL-04). Access is restricted to users with the
     * OWNER or ADMIN role.
     *
     * @param startDate the start of the period (inclusive), format YYYY-MM-DD
     * @param endDate   the end of the period (inclusive), format YYYY-MM-DD
     * @return the aggregated financial report for the caller's hotel
     */
    @GetMapping("/owner")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<OwnerFinancialReportDto> getOwnerFinancialReport(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate startDate,
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate endDate) {
        final UUID hotelId = Objects.requireNonNull(extractHotelId());
        log.info("REST request for owner financial report | hotelId={} | from {} to {}", hotelId, startDate, endDate);
        final OwnerFinancialReportDto report = ownerReportService.getFinancialReport(hotelId, startDate, endDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Returns the same aggregated totals as {@link #getOwnerFinancialReport},
     * without the per-invoice list, scoped to the caller's hotel (T-BILL-04).
     * Backs the Dashboard's revenue widget, which only needs these numbers —
     * not a full unpaginated invoice list. Access is restricted to users with
     * the OWNER or ADMIN role.
     *
     * @param startDate the start of the period (inclusive), format YYYY-MM-DD
     * @param endDate   the end of the period (inclusive), format YYYY-MM-DD
     * @return the aggregated financial summary for the caller's hotel
     */
    @GetMapping("/owner/summary")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<OwnerFinancialSummaryDto> getOwnerFinancialSummary(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate startDate,
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate endDate) {
        final UUID hotelId = Objects.requireNonNull(extractHotelId());
        log.info("REST request for owner financial summary | hotelId={} | from {} to {}", hotelId, startDate, endDate);
        final OwnerFinancialSummaryDto summary = ownerReportService.getFinancialSummary(hotelId, startDate, endDate);
        return ResponseEntity.ok(summary);
    }

    /**
     * Returns the RevPAR/ADR/Occupancy trend for a date range, scoped to the
     * caller's hotel (epic C4). {@code startDate}/{@code endDate} are
     * inclusive, same convention as {@link #getOwnerFinancialReport}. Access
     * is restricted to users with the OWNER or ADMIN role.
     *
     * @param startDate   the start of the period (inclusive), format YYYY-MM-DD
     * @param endDate     the end of the period (inclusive), format YYYY-MM-DD
     * @param granularity the time-bucket size
     * @return the KPI trend report for the caller's hotel
     */
    @GetMapping("/kpi")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<KpiReportDto> getKpiReport(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate startDate,
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate endDate,
            @NonNull @RequestParam final ReportGranularity granularity) {
        final UUID hotelId = Objects.requireNonNull(extractHotelId());
        log.info("REST request for KPI report | hotelId={} | from {} to {} | granularity={}",
                hotelId, startDate, endDate, granularity);
        final KpiReportDto report = kpiReportService.getKpiReport(hotelId, startDate, endDate, granularity);
        return ResponseEntity.ok(report);
    }

    private UUID extractHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr) || hotelIdStr.isBlank()) {
            throw new IllegalStateException("HOTEL_ID_NOT_AVAILABLE");
        }
        return UUID.fromString(hotelIdStr);
    }
}
