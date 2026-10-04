package com.hotelpms.frontdesk.nightaudit.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * One night-audit snapshot of the front-desk figures, for a single business date.
 *
 * @param date           the business date the snapshot belongs to
 * @param arrivals       arrivals expected that day, no-shows included
 * @param departures     departures that day
 * @param guestsInHouse  guests checked in when the audit ran
 * @param availableRooms rooms available when the audit ran
 */
public record DaySheetTrendPoint(
        LocalDate date,
        int arrivals,
        int departures,
        long guestsInHouse,
        int availableRooms) implements Serializable {
}
