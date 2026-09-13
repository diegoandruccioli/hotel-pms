package com.hotelpms.frontdesk.housekeeping.dto;

import com.hotelpms.frontdesk.housekeeping.domain.HousekeepingTaskType;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * One room's worth of housekeeping worksheet detail. Deliberately carries no
 * guest name or any other guest-identifying field — GDPR data minimization,
 * since this row is meant to end up on a printed sheet that leaves the
 * front-of-house system (e.g. clipped to a cleaning cart).
 *
 * @param roomNumber            the room number
 * @param roomTypeName          the room type's display name
 * @param taskType              why this room is on the worksheet
 * @param currentStatus         the room's actual current housekeeping status
 *                              (independent of {@code taskType} — e.g. a
 *                              {@code DEPARTURE} row's room is still {@code
 *                              OCCUPIED} until the guest actually leaves)
 * @param pax                   number of guests currently in the room (0 for
 *                              {@code VACANT_DIRTY}/{@code MAINTENANCE} rows)
 * @param expectedDepartureDate the stay's expected check-out date, or {@code
 *                              null} for {@code ARRIVAL_PREP}/{@code
 *                              VACANT_DIRTY}/{@code MAINTENANCE} rows
 * @param turnover              {@code true} when a new arrival is booked into
 *                              this same room the same day (only meaningful
 *                              on a {@code DEPARTURE} row — see {@link
 *                              HousekeepingTaskType#DEPARTURE})
 * @param departureDateUnknown  {@code true} when this stay predates the
 *                              {@code expectedCheckOutDate} field and the
 *                              real departure date can't be determined — the
 *                              row is conservatively classified {@code
 *                              STAYOVER} rather than silently dropped
 */
public record HousekeepingRow(
        String roomNumber,
        String roomTypeName,
        HousekeepingTaskType taskType,
        RoomStatus currentStatus,
        int pax,
        LocalDate expectedDepartureDate,
        boolean turnover,
        boolean departureDateUnknown) implements Serializable {
}
