package com.hotelpms.frontdesk.nightaudit.controller;

import com.hotelpms.frontdesk.nightaudit.dto.NightAuditRunResponse;
import com.hotelpms.frontdesk.nightaudit.service.NightAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Manual trigger + history for the night audit — closes the operational/
 * financial books for a business date. Includes the cash-closing summary
 * (financial data), so ADMIN/OWNER only, unlike the day-sheet (no figures,
 * open to every role).
 */
@RestController
@RequestMapping("/api/v1/frontdesk/night-audit")
@RequiredArgsConstructor
public class NightAuditController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String ROLE_ADMIN_OR_OWNER = "hasAnyRole('ADMIN', 'OWNER')";

    private final NightAuditService nightAuditService;

    /**
     * Runs (or retries, if the existing row for this date is FAILED) the
     * night audit for the caller's hotel and the given business date.
     * Returns 200 with the resulting run even when it ends FAILED — the
     * caller sees the failure reason in the body rather than a bare 500.
     *
     * @param date the business date to close
     * @return the resulting run
     */
    @PostMapping
    @PreAuthorize(ROLE_ADMIN_OR_OWNER)
    public ResponseEntity<NightAuditRunResponse> runNightAudit(
            @NonNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        final String runBy = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(nightAuditService.run(date, runBy));
    }

    /**
     * Lists the caller's hotel's night-audit history, newest business date first.
     *
     * @param pageable pagination parameters
     * @return a page of runs
     */
    @GetMapping
    @PreAuthorize(ROLE_ADMIN_OR_OWNER)
    public ResponseEntity<Page<NightAuditRunResponse>> getHistory(
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "businessDate",
                    direction = Sort.Direction.DESC) final Pageable pageable) {
        return ResponseEntity.ok(nightAuditService.getHistory(pageable));
    }
}
