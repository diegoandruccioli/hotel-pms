package com.hotelpms.frontdesk.housekeeping.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Resolves "today" for the housekeeping worksheet from the hotel's own
 * timezone and day-cutoff-hour settings — deliberately never a bare {@code
 * LocalDate.now()} (JVM-zone midnight rollover), so a worksheet requested at
 * 02:00 local time still resolves to yesterday's business date, matching what
 * the night shift is actually working on.
 *
 * <p>This is intentionally narrow: only the housekeeping worksheet uses this
 * resolver. The rest of the codebase's date logic (night audit, invoice
 * numbering, retention windows, etc.) still uses {@code LocalDate.now()} on
 * the JVM's default zone — see the repo-wide note in the housekeeping
 * worksheet's implementation plan. Unifying every date decision onto a
 * hotel-local {@code Clock} is a separate, larger change, not folded in here.
 */
@FunctionalInterface
public interface BusinessDateResolver {

    /**
     * Resolves the current housekeeping business date for a hotel.
     *
     * @param hotelId the hotel to resolve for
     * @return the resolved business date
     */
    LocalDate resolve(UUID hotelId);
}
