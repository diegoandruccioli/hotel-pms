package com.hotelpms.frontdesk.housekeeping.dto;

import java.time.LocalDate;

/**
 * The hotel's currently resolved housekeeping business date, plus the
 * settings that produced it — so the frontend can explain the "why" (e.g.
 * "before 04:00 local time, still showing yesterday") without re-implementing
 * {@code BusinessDateResolver}'s rule in JavaScript.
 *
 * @param businessDate the resolved business date
 * @param timezone     the IANA timezone used to resolve it
 * @param cutoffHour   the hour (0-12, hotel-local) below which the date still
 *                     resolves to yesterday
 */
public record BusinessDateResponse(LocalDate businessDate, String timezone, int cutoffHour) {
}
