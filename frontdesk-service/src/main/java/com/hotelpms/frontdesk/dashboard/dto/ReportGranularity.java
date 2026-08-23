package com.hotelpms.frontdesk.dashboard.dto;

/**
 * Time-bucket size for KPI/occupancy trend reports. Validated here (an enum,
 * bound by Spring's {@code @RequestParam} conversion) before it can ever
 * reach a SQL {@code date_trunc(...)} call as a bind parameter — never
 * accepted as a raw string and concatenated into a query.
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
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
