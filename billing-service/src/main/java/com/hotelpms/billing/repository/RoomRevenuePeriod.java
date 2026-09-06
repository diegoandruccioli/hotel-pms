package com.hotelpms.billing.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Spring Data interface projection for one time-bucket row of the room
 * revenue aggregate. Backs the KPI trend report (epic C4) — a single grouped
 * query instead of loading every charge and summing in Java.
 */
public interface RoomRevenuePeriod {

    /**
     * The start of this time bucket.
     *
     * @return the bucket start date
     */
    LocalDate getPeriodStart();

    /**
     * Room-night revenue attributed to the calendar nights that fall within
     * this bucket — each charge's amount is spread across the nights it
     * covers, not attributed wholesale to the bucket of its issue date.
     *
     * @return the total revenue for this bucket
     */
    BigDecimal getTotalRevenue();
}
