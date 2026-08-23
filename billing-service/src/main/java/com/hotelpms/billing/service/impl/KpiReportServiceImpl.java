package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.FrontdeskRoomsClient;
import com.hotelpms.billing.client.dto.OccupancyPeriodResponse;
import com.hotelpms.billing.client.dto.OccupancySummaryResponse;
import com.hotelpms.billing.dto.KpiPeriodDto;
import com.hotelpms.billing.dto.KpiReportDto;
import com.hotelpms.billing.dto.ReportGranularity;
import com.hotelpms.billing.repository.InvoiceChargeRepository;
import com.hotelpms.billing.repository.RoomRevenuePeriod;
import com.hotelpms.billing.service.KpiReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Implementation of {@link KpiReportService}. Joins two authoritative
 * sources that neither service has alone: room revenue lives in
 * billing-service's own {@code invoice_charges}, room count and occupied
 * room-nights live in frontdesk-service's {@code rooms}/{@code stays}
 * (fetched via {@link FrontdeskRoomsClient}). The merge happens here, in
 * Java, on the two already-bucketed period lists — not a distributed join.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("checkstyle:DesignForExtension")
public class KpiReportServiceImpl implements KpiReportService {

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 4;

    private final InvoiceChargeRepository invoiceChargeRepository;
    private final FrontdeskRoomsClient frontdeskRoomsClient;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public KpiReportDto getKpiReport(final UUID hotelId, final LocalDate startDate, final LocalDate endDate,
            final ReportGranularity granularity) {
        log.info("Generating KPI report for hotelId={} from {} to {} granularity={}",
                hotelId, startDate, endDate, granularity);

        // startDate/endDate are inclusive on this endpoint's public contract,
        // matching /reports/owner and /reports/owner/summary — converted to
        // an exclusive upper bound once here, used consistently for both the
        // revenue query and the frontdesk occupancy call.
        final LocalDate exclusiveEnd = endDate.plusDays(1);
        final LocalDateTime start = startDate.atStartOfDay();
        final LocalDateTime end = exclusiveEnd.atStartOfDay();

        final List<RoomRevenuePeriod> revenueRows = invoiceChargeRepository
                .sumRoomRevenueByHotelIdGroupedByPeriod(hotelId, start, end, granularity.sqlValue());
        final OccupancySummaryResponse occupancy =
                frontdeskRoomsClient.getOccupancySummary(startDate, exclusiveEnd, granularity.name());

        final int totalRooms = occupancy.totalRooms();
        final Map<LocalDate, BigDecimal> revenueByPeriod = new TreeMap<>();
        for (final RoomRevenuePeriod row : revenueRows) {
            revenueByPeriod.put(row.getPeriodStart(), row.getTotalRevenue());
        }
        final Map<LocalDate, Long> nightsByPeriod = new TreeMap<>();
        for (final OccupancyPeriodResponse row : occupancy.periods()) {
            nightsByPeriod.put(row.periodStart(), row.occupiedRoomNights());
        }

        // Outer join on periodStart: a bucket may have revenue but no
        // completed stays yet (or vice versa) — every bucket either side
        // reported gets a row, missing values default to zero.
        final Set<LocalDate> allPeriodStarts = new TreeSet<>();
        allPeriodStarts.addAll(revenueByPeriod.keySet());
        allPeriodStarts.addAll(nightsByPeriod.keySet());

        final List<KpiPeriodDto> periods = allPeriodStarts.stream()
                .map(periodStart -> buildPeriod(periodStart, startDate, exclusiveEnd, totalRooms, granularity,
                        revenueByPeriod.getOrDefault(periodStart, BigDecimal.ZERO),
                        nightsByPeriod.getOrDefault(periodStart, 0L)))
                .toList();

        final KpiPeriodDto totals = buildTotals(startDate, exclusiveEnd, totalRooms, periods);

        return new KpiReportDto(periods, totals);
    }

    private KpiPeriodDto buildPeriod(final LocalDate periodStart, final LocalDate queryStart,
            final LocalDate queryEnd, final int totalRooms, final ReportGranularity granularity,
            final BigDecimal revenue, final long occupiedNights) {
        final long availableNights = totalRooms * daysInBucketWithinWindow(
                periodStart, queryStart, queryEnd, granularity);
        return toDto(periodStart, revenue, occupiedNights, availableNights);
    }

    private KpiPeriodDto buildTotals(final LocalDate startDate, final LocalDate exclusiveEnd,
            final int totalRooms, final List<KpiPeriodDto> periods) {
        final BigDecimal revenue = periods.stream()
                .map(KpiPeriodDto::totalRoomRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final long occupiedNights = periods.stream().mapToLong(KpiPeriodDto::occupiedRoomNights).sum();
        final long totalDays = ChronoUnit.DAYS.between(startDate, exclusiveEnd);
        final long availableNights = totalRooms * totalDays;
        return toDto(startDate, revenue, occupiedNights, availableNights);
    }

    private KpiPeriodDto toDto(final LocalDate periodStart, final BigDecimal revenue,
            final long occupiedNights, final long availableNights) {
        final BigDecimal adr = occupiedNights > 0
                ? revenue.divide(BigDecimal.valueOf(occupiedNights), MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        final BigDecimal revpar = availableNights > 0
                ? revenue.divide(BigDecimal.valueOf(availableNights), MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        final BigDecimal occupancyRate = availableNights > 0
                ? BigDecimal.valueOf(occupiedNights)
                        .divide(BigDecimal.valueOf(availableNights), RATE_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new KpiPeriodDto(periodStart, revenue, occupiedNights, availableNights, adr, revpar, occupancyRate);
    }

    /**
     * Days available in this bucket, clipped to the actual query window —
     * not just the bucket's natural length. Without clipping, a boundary
     * bucket (e.g. the first week of a range starting mid-week, or a month
     * bucket for a range that starts/ends mid-month) would use its full
     * natural length as the denominator while only holding partial-period
     * revenue/nights, overstating available room-nights and understating
     * RevPAR/Occupancy for that bucket.
     *
     * @param periodStart the bucket's start, as returned by {@code date_trunc} (always a natural boundary)
     * @param queryStart  the report's requested start date
     * @param queryEnd    the report's requested end date (exclusive)
     * @param granularity the bucket size
     * @return the number of days of this bucket that fall within [queryStart, queryEnd)
     */
    private static long daysInBucketWithinWindow(final LocalDate periodStart, final LocalDate queryStart,
            final LocalDate queryEnd, final ReportGranularity granularity) {
        final LocalDate bucketEnd = switch (granularity) {
            case DAY -> periodStart.plusDays(1);
            case WEEK -> periodStart.plusWeeks(1);
            case MONTH -> periodStart.plusMonths(1);
        };
        final LocalDate overlapStart = periodStart.isAfter(queryStart) ? periodStart : queryStart;
        final LocalDate overlapEnd = bucketEnd.isBefore(queryEnd) ? bucketEnd : queryEnd;
        return Math.max(0L, ChronoUnit.DAYS.between(overlapStart, overlapEnd));
    }
}
