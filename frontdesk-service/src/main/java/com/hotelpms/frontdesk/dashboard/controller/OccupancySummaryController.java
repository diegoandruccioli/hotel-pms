package com.hotelpms.frontdesk.dashboard.controller;

import com.hotelpms.frontdesk.dashboard.dto.OccupancySummaryResponse;
import com.hotelpms.frontdesk.dashboard.dto.ReportGranularity;
import com.hotelpms.frontdesk.dashboard.service.OccupancySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller for the room-occupancy trend summary backing the KPI report
 * (epic C4). Consumed by billing-service's {@code /api/v1/reports/kpi} via
 * an internal Feign call, never called directly by the frontend.
 */
@RestController
@RequestMapping("/api/v1/frontdesk/occupancy-summary")
@RequiredArgsConstructor
public class OccupancySummaryController {

    private final OccupancySummaryService occupancySummaryService;

    /**
     * Returns the room count and bucketed occupied-room-nights trend for a
     * date range, scoped to the caller's hotel. Restricted to ADMIN/OWNER —
     * same audience as the KPI report this backs, even though the room
     * counts here carry no revenue figures of their own.
     *
     * @param dateFrom    start of the window (inclusive)
     * @param dateTo      end of the window (exclusive)
     * @param granularity the time-bucket size
     * @return the occupancy summary
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<OccupancySummaryResponse> getOccupancySummary(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate dateFrom,
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate dateTo,
            @NonNull @RequestParam final ReportGranularity granularity) {
        return ResponseEntity.ok(
                occupancySummaryService.getOccupancySummary(dateFrom, dateTo, granularity, resolveHotelId()));
    }

    /**
     * Extracts the hotel UUID from the authenticated user's security context.
     * The value is set by the internal auth filter from the {@code X-Auth-Hotel}
     * header injected by the API Gateway.
     *
     * @return the hotel UUID of the authenticated user
     */
    private UUID resolveHotelId() {
        final Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        return UUID.fromString(String.valueOf(details));
    }
}
