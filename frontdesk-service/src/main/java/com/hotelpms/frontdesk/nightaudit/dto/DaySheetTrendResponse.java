package com.hotelpms.frontdesk.nightaudit.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Recent night-audit snapshots of the front-desk figures, oldest first. A date
 * with no completed audit is simply absent rather than present with zeros.
 *
 * @param from   first date of the requested window (inclusive)
 * @param to     last date of the requested window (inclusive)
 * @param points one snapshot per completed audit inside the window
 */
public record DaySheetTrendResponse(
        LocalDate from,
        LocalDate to,
        List<DaySheetTrendPoint> points) implements Serializable {

    /**
     * Compact constructor — defensive copy of the mutable collection field.
     */
    public DaySheetTrendResponse {
        points = points == null ? List.of() : List.copyOf(points);
    }

    /**
     * Returns a copy of the points list to prevent external modification.
     *
     * @return one snapshot per completed audit inside the window
     */
    @Override
    public List<DaySheetTrendPoint> points() {
        return List.copyOf(points);
    }
}
