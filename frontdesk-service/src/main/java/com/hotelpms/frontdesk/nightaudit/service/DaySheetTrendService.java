package com.hotelpms.frontdesk.nightaudit.service;

import com.hotelpms.frontdesk.nightaudit.dto.DaySheetTrendResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reads the recent night-audit snapshots back as a trend for the dashboard.
 */
@FunctionalInterface
public interface DaySheetTrendService {

    /**
     * Returns the completed night-audit snapshots of the {@code days} business
     * dates before {@code date}, oldest first. {@code date} itself is excluded:
     * today's value is the live day-sheet.
     *
     * <p>Each snapshot is taken at the audit (03:30 the next morning), so
     * {@code arrivals} counts expected arrivals including no-shows and
     * {@code guestsInHouse} counts guests checked in when the audit ran.
     *
     * @param date    the reference date (excluded from the window)
     * @param days    window length, 1 to 14
     * @param hotelId the hotel UUID (tenant isolation)
     * @return the snapshots found; dates without a completed audit are absent
     */
    DaySheetTrendResponse getTrend(LocalDate date, int days, UUID hotelId);
}
