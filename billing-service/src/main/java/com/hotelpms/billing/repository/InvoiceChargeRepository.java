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
     * report (epic C4). {@code InvoiceCharge} has no {@code hotel_id} column
     * of its own — reached only through {@code invoice.hotel_id}, hence the
     * explicit join, same reasoning that keeps this repository off
     * {@code TenantIsolationArchTest}'s {@code TENANT_ROOT_REPOSITORIES}
     * allowlist. Native query: {@code date_trunc} is PostgreSQL-specific, and
     * this project targets only PostgreSQL (see Flyway migrations) — no
     * portability concern traded away. {@code granularity} is always a
     * validated {@code ReportGranularity} enum value by the time it reaches
     * this bind parameter (see {@code OwnerReportController}), never a raw
     * client-supplied string.
     *
     * @param hotelId     the hotel UUID (tenant isolation, via the invoice join)
     * @param start       beginning of the window (inclusive), by issue date
     * @param end         end of the window (exclusive), by issue date
     * @param granularity {@code date_trunc}'s bucket size — {@code "day"}, {@code "week"}, or {@code "month"}
     * @return one row per non-empty bucket, ordered by {@code periodStart}
     */
    @Query(value = "SELECT date_trunc(:granularity, i.issue_date)::date AS periodStart, "
            + "COALESCE(SUM(c.amount), 0) AS totalRevenue "
            + "FROM invoice_charges c JOIN invoices i ON c.invoice_id = i.id "
            + "WHERE i.hotel_id = :hotelId AND c.type = 'ROOM_NIGHT' "
            + "AND i.issue_date >= :start AND i.issue_date < :end "
            + "GROUP BY date_trunc(:granularity, i.issue_date) "
            + "ORDER BY periodStart",
            nativeQuery = true)
    List<RoomRevenuePeriod> sumRoomRevenueByHotelIdGroupedByPeriod(
            @Param("hotelId") UUID hotelId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, @Param("granularity") String granularity);
}
