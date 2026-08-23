package com.hotelpms.billing.dto;

import java.util.Locale;

/**
 * Time-bucket size for the KPI trend report. Validated here (an enum, bound
 * by Spring's {@code @RequestParam} conversion) before it can ever reach a
 * SQL {@code date_trunc(...)} call as a bind parameter — never accepted as a
 * raw string and concatenated into a query. Mirrors frontdesk-service's own
 * {@code ReportGranularity} by name — the two modules have no shared DTO
 * layer for internal client contracts.
 */
public enum ReportGranularity {
    DAY,
    WEEK,
    MONTH;

    /**
     * Returns the lowercase form {@code date_trunc}'s first argument expects.
     *
     * @return the PostgreSQL date-part literal
     */
    public String sqlValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
