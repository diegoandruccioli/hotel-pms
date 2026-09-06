package com.hotelpms.frontdesk.nightaudit.service;

import com.hotelpms.frontdesk.nightaudit.dto.NightAuditRunResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.time.LocalDate;

/**
 * Closes the operational/financial books for a single business date:
 * auto-detects no-shows, snapshots occupancy/arrivals/departures, and
 * records a cash-by-method summary from billing-service.
 */
public interface NightAuditService {

    /**
     * Runs (or re-runs, if the existing row for this date is FAILED) the
     * night audit for the caller's hotel and the given business date.
     *
     * @param businessDate the business date to close
     * @param runBy        who/what triggered the run
     * @return the resulting run, COMPLETED or FAILED
     */
    NightAuditRunResponse run(@NonNull LocalDate businessDate, @NonNull String runBy);

    /**
     * Lists the caller's hotel's night-audit history, newest business date first.
     *
     * @param pageable pagination and sorting
     * @return a page of runs
     */
    Page<NightAuditRunResponse> getHistory(Pageable pageable);
}
