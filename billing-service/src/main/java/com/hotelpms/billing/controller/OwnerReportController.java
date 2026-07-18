package com.hotelpms.billing.controller;

import com.hotelpms.billing.dto.OwnerFinancialReportDto;
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

    private UUID extractHotelId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof String hotelIdStr) || hotelIdStr.isBlank()) {
            throw new IllegalStateException("HOTEL_ID_NOT_AVAILABLE");
        }
        return UUID.fromString(hotelIdStr);
    }
}
