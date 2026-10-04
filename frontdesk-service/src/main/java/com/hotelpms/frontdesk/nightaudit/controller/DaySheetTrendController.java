package com.hotelpms.frontdesk.nightaudit.controller;

import com.hotelpms.frontdesk.nightaudit.dto.DaySheetTrendResponse;
import com.hotelpms.frontdesk.nightaudit.service.DaySheetTrendService;
import com.hotelpms.internalauth.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Controller for the day-sheet trend: recent night-audit snapshots the
 * dashboard turns into deltas and sparklines.
 */
@RestController
@RequestMapping("/api/v1/frontdesk/day-sheet")
@RequiredArgsConstructor
public class DaySheetTrendController {

    private final DaySheetTrendService daySheetTrendService;

    /**
     * Returns the completed night-audit snapshots of the {@code days} dates
     * before {@code date}, scoped to the caller's hotel. Open to the same roles
     * as the day-sheet itself; like it, it carries no financial figures.
     *
     * @param date the reference date, excluded from the window
     * @param days window length, 1 to 14 (default 7)
     * @return the snapshots, oldest first
     */
    @GetMapping("/trend")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'RECEPTIONIST')")
    public ResponseEntity<DaySheetTrendResponse> getTrend(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date,
            @RequestParam(defaultValue = "7") final int days) {
        return ResponseEntity.ok(daySheetTrendService.getTrend(date, days, TenantContext.resolveHotelId()));
    }
}
