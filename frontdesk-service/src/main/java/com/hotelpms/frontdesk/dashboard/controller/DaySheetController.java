package com.hotelpms.frontdesk.dashboard.controller;

import com.hotelpms.frontdesk.dashboard.dto.DaySheetResponse;
import com.hotelpms.frontdesk.dashboard.service.DaySheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller for the front-desk day-sheet summary.
 */
@RestController
@RequestMapping("/api/v1/frontdesk/day-sheet")
@RequiredArgsConstructor
public class DaySheetController {

    private final DaySheetService daySheetService;

    /**
     * Returns the front-desk operational summary for a single date, scoped
     * to the caller's hotel. Open to every role (RECEPTIONIST/OWNER/ADMIN
     * all land on the Dashboard) — unlike {@code /api/v1/reports/owner},
     * this carries no financial figures.
     *
     * @param date the date to summarize
     * @return the day-sheet summary
     */
    @GetMapping
    public ResponseEntity<DaySheetResponse> getDaySheet(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        return ResponseEntity.ok(daySheetService.getDaySheet(date, resolveHotelId()));
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
