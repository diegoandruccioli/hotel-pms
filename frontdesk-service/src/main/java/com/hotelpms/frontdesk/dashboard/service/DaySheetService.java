package com.hotelpms.frontdesk.dashboard.service;

import com.hotelpms.frontdesk.dashboard.dto.DaySheetResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for computing the front-desk day-sheet summary.
 */
@FunctionalInterface
public interface DaySheetService {

    /**
     * Computes the day-sheet summary for a single date, scoped to a hotel.
     *
     * @param date    the date to summarize
     * @param hotelId the hotel UUID (tenant isolation)
     * @return the day-sheet summary
     */
    DaySheetResponse getDaySheet(LocalDate date, UUID hotelId);
}
