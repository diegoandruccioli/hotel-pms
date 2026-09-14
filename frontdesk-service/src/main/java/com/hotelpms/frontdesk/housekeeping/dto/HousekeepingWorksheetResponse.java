package com.hotelpms.frontdesk.housekeeping.dto;

import com.hotelpms.frontdesk.housekeeping.domain.HousekeepingTaskType;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The housekeeping worksheet for one hotel and one business date.
 *
 * @param date         the business date this worksheet is for
 * @param generatedAt  when this worksheet was generated — printed on every
 *                      page of the PDF so a stale copy can be told apart from
 *                      a re-print
 * @param provisional  {@code true} when the night audit for {@code date - 1}
 *                      has not yet completed — late check-outs, stay
 *                      extensions, same-day bookings and no-shows can still
 *                      change these rows until then. Always {@code true} for
 *                      a future date
 * @param hotelName     the hotel's display name, for the PDF header
 * @param rows          one row per room that needs attention, grouped by
 *                      {@link HousekeepingTaskType} and sorted by room number
 *                      within each group
 * @param summary       room count per {@link HousekeepingTaskType}; a type
 *                      with no rooms is simply absent rather than present
 *                      with 0
 */
public record HousekeepingWorksheetResponse(
        LocalDate date,
        LocalDateTime generatedAt,
        boolean provisional,
        String hotelName,
        List<HousekeepingRow> rows,
        Map<HousekeepingTaskType, Integer> summary) implements Serializable {

    /**
     * Compact constructor — defensive copy of the mutable collection fields.
     */
    public HousekeepingWorksheetResponse {
        rows = rows == null ? List.of() : List.copyOf(rows);
        summary = summary == null ? Map.of() : Map.copyOf(summary);
    }

    /**
     * Returns a copy of the rows list to prevent external modification.
     *
     * @return one row per room that needs attention
     */
    @Override
    public List<HousekeepingRow> rows() {
        return List.copyOf(rows);
    }

    /**
     * Returns a copy of the summary map to prevent external modification.
     *
     * @return room count per {@link HousekeepingTaskType}
     */
    @Override
    public Map<HousekeepingTaskType, Integer> summary() {
        return Map.copyOf(summary);
    }
}
