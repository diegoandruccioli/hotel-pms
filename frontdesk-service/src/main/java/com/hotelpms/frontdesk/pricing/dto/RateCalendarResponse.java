package com.hotelpms.frontdesk.pricing.dto;

import java.util.List;

/**
 * The full rate calendar grid for a hotel over a requested date range: one
 * row per room type.
 *
 * @param rows the room type rows, in the same order as
 *             {@code RoomTypeService.getAllRoomTypes}
 */
public record RateCalendarResponse(List<RateCalendarRow> rows) {

    /**
     * Defensive copy — a mutable {@code List} field on a record is otherwise a
     * shared-reference leak.
     *
     * @param rows the room type rows
     */
    public RateCalendarResponse {
        rows = rows == null ? null : List.copyOf(rows);
    }

    /**
     * Defensive accessor mirroring the compact constructor's copy.
     *
     * @return an unmodifiable copy of the rows
     */
    @Override
    public List<RateCalendarRow> rows() {
        return rows == null ? null : List.copyOf(rows);
    }
}
