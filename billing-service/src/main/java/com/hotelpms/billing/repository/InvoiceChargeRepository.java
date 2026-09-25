package com.hotelpms.billing.repository;

import com.hotelpms.billing.domain.InvoiceCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JPA Repository for InvoiceCharge entities.
 */
@Repository
public interface InvoiceChargeRepository extends JpaRepository<InvoiceCharge, UUID> {

    /**
     * Finds all charges belonging to a specific invoice.
     *
     * @param invoiceId the invoice UUID
     * @return list of charges for the given invoice
     */
    List<InvoiceCharge> findByInvoiceId(UUID invoiceId);

    /**
     * Sums room-night revenue per time bucket for a hotel, for the KPI trend
     * report (epic C4), attributed to the calendar nights each charge actually
     * covers rather than dumped entirely into the bucket containing its
     * {@code issue_date}.
     *
     * <p>A {@code ROOM_NIGHT} charge is posted once, at check-in, for the
     * stay's full length: {@code Invoice.issueDate} is the check-in instant,
     * and {@code InvoiceCharge.nights} is how many nights forward from it the
     * charge covers — {@code [issueDate, issueDate + nights)}. There is no
     * per-night charge or stored date range, only that single instant plus a
     * night count, so this evenly distributes {@code amount} across
     * {@code nights} calendar nights starting at {@code issue_date::date}
     * (via {@code generate_series}) and only sums the nights that actually
     * fall in {@code [start, end)}, before bucketing by {@code granularity}.
     * A charge with a null/non-positive {@code nights} (defensive only — the
     * write side always sets a positive value) is treated as covering exactly
     * one night, its issue date.
     *
     * <p>Previously this attributed a charge's ENTIRE amount to whichever
     * single bucket contained its {@code issue_date} (the check-in day) — so
     * a 3-night stay checked in on day 1 posted all 3 nights' revenue to day
     * 1's bucket, none to days 2-3, and a report window that happened to
     * exclude the check-in day but include the later nights of an in-progress
     * stay saw zero revenue for real, currently-occupied nights (found in
     * live QA, 2026-09, alongside the related occupied-room-nights bug in
     * frontdesk-service's {@code StayRepository}).
     *
     * <p>{@code InvoiceCharge} has no {@code hotel_id} column of its own —
     * reached only through {@code invoice.hotel_id}, hence the explicit join,
     * same reasoning that keeps this repository off
     * {@code TenantIsolationArchTest}'s {@code TENANT_ROOT_REPOSITORIES}
     * allowlist. Native query: {@code date_trunc} and {@code generate_series}
     * are PostgreSQL-specific, and this project targets only PostgreSQL (see
     * Flyway migrations) — no portability concern traded away.
     * {@code granularity} is always a validated {@code ReportGranularity}
     * enum value by the time it reaches this bind parameter (see
     * {@code OwnerReportController}), never a raw client-supplied string.
     *
     * @param hotelId     the hotel UUID (tenant isolation, via the invoice join)
     * @param start       beginning of the window (inclusive)
     * @param end         end of the window (exclusive)
     * @param granularity {@code date_trunc}'s bucket size — {@code "day"}, {@code "week"}, or {@code "month"}
     * @return one row per non-empty bucket, ordered by {@code periodStart}
     */
    @Query(value = "WITH nightly AS ("
            + "  SELECT i.issue_date::date + gs.n AS night, "
            + "         c.amount / GREATEST(COALESCE(c.nights, 1), 1) AS nightly_amount "
            + "  FROM invoice_charges c "
            + "  JOIN invoices i ON c.invoice_id = i.id "
            + "  CROSS JOIN LATERAL generate_series(0, GREATEST(COALESCE(c.nights, 1), 1) - 1) AS gs(n) "
            + "  WHERE i.hotel_id = :hotelId AND c.type = 'ROOM_NIGHT'"
            + ") "
            + "SELECT date_trunc(:granularity, night)::date AS periodStart, "
            + "COALESCE(SUM(nightly_amount), 0) AS totalRevenue "
            + "FROM nightly "
            + "WHERE night >= CAST(:start AS date) AND night < CAST(:end AS date) "
            // GROUP BY the output alias, not a second date_trunc(:granularity, ...) —
            // two separate bind-parameter occurrences of the same expression are NOT
            // recognized by Postgres as equal for GROUP BY validity (42803: "column
            // must appear in the GROUP BY clause"), even though both bind to the same
            // runtime value. Grouping by the SELECT-list alias sidesteps the issue.
            + "GROUP BY periodStart "
            + "ORDER BY periodStart",
            nativeQuery = true)
    List<RoomRevenuePeriod> sumRoomRevenueByHotelIdGroupedByPeriod(
            @Param("hotelId") UUID hotelId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, @Param("granularity") String granularity);
}
