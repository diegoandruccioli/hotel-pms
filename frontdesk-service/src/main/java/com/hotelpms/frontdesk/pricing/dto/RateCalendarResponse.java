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
}
