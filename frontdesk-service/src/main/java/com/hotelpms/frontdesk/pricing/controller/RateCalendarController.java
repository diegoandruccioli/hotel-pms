package com.hotelpms.frontdesk.pricing.controller;

import com.hotelpms.frontdesk.pricing.dto.RateBulkApplyRequest;
import com.hotelpms.frontdesk.pricing.dto.RateCalendarResponse;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;
import com.hotelpms.frontdesk.pricing.service.RateCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controller for the rate calendar: a whole-hotel, whole-range read of
 * resolved nightly prices, plus a bulk-apply write that splits/trims
 * existing seasons instead of rejecting on overlap. Distinct from
 * {@link RateSeasonController}, which manages one season at a time for one
 * room type. Read access matches {@code /api/v1/room-types} (open to any
 * authenticated operational role); the bulk-apply write is restricted to
 * ADMIN/OWNER, same policy as {@code RateSeasonController}'s writes — this
 * mirrors an existing rule on the same domain, not new authorization surface.
 */
@RestController
@RequestMapping("/api/v1/rate-calendar")
@RequiredArgsConstructor
public class RateCalendarController {

    private final RateCalendarService rateCalendarService;

    /**
     * Returns the resolved nightly price for every room type over
     * {@code [from, to]} (both inclusive).
     *
     * @param from the range start (inclusive)
     * @param to   the range end (inclusive)
     * @return the calendar grid
     */
    @GetMapping
    public ResponseEntity<RateCalendarResponse> getCalendar(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate from,
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate to) {
        return ResponseEntity.ok(rateCalendarService.getCalendar(resolveHotelId(), from, to));
    }

    /**
     * Applies one nightly price to a date range across several room types.
     * Restricted to ADMIN/OWNER.
     *
     * @param request the room types, range and price to apply
     * @return the newly created season for each room type
     */
    @PostMapping("/bulk-apply")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<RateSeasonResponse>> bulkApply(
            @NonNull @Valid @RequestBody final RateBulkApplyRequest request) {
        return ResponseEntity.ok(rateCalendarService.bulkApply(resolveHotelId(), request));
    }

    /**
     * Extracts the hotel UUID from the current security context details.
     *
     * @return the hotel UUID of the authenticated user
     */
    private UUID resolveHotelId() {
        final Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        return UUID.fromString(String.valueOf(details));
    }
}
