package com.hotelpms.frontdesk.housekeeping.domain;

/**
 * What kind of attention a room needs on the housekeeping worksheet for a
 * given business date. Purely a worksheet-classification concept — not
 * persisted, and distinct from {@link com.hotelpms.frontdesk.rooms.domain.RoomStatus},
 * which tracks the room's actual current housekeeping state.
 */
public enum HousekeepingTaskType {

    /**
     * A checked-in stay whose expected (or overdue) check-out date is on or
     * before the worksheet date — the room needs a full clean once the guest
     * leaves. A same-day turnover (a new arrival booked into the same room)
     * is not a separate task type: it stays {@code DEPARTURE}, flagged via
     * the row's {@code turnover} field, since it's a single higher-priority
     * cleaning job either way.
     */
    DEPARTURE,

    /**
     * A checked-in stay continuing past the worksheet date — typically just a
     * refresh/service visit, not a full turnover clean.
     */
    STAYOVER,

    /**
     * A reservation checking in on the worksheet date whose room is not also
     * departing that day (a same-day turnover is folded into the {@code
     * DEPARTURE} row instead of appearing here separately).
     */
    ARRIVAL_PREP,

    /**
     * A vacant room already marked {@code DIRTY} — no guest currently in it,
     * simply awaiting cleaning.
     */
    VACANT_DIRTY,

    /**
     * A room under {@code MAINTENANCE} — listed for staff visibility only,
     * not actionable from the worksheet.
     */
    MAINTENANCE
}
