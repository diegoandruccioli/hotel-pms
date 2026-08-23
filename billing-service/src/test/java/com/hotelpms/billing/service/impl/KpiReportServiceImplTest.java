package com.hotelpms.billing.service.impl;

import com.hotelpms.billing.client.FrontdeskRoomsClient;
import com.hotelpms.billing.client.dto.OccupancyPeriodResponse;
import com.hotelpms.billing.client.dto.OccupancySummaryResponse;
import com.hotelpms.billing.dto.KpiPeriodDto;
import com.hotelpms.billing.dto.KpiReportDto;
import com.hotelpms.billing.dto.ReportGranularity;
import com.hotelpms.billing.repository.InvoiceChargeRepository;
import com.hotelpms.billing.repository.RoomRevenuePeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiReportServiceImplTest {

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 8, 1);
    private static final LocalDate DAY_EXCLUSIVE_END = LocalDate.of(2026, 8, 2);
    private static final int TEN_ROOMS = 10;
    private static final BigDecimal REVENUE_1000 = new BigDecimal("1000.00");
    private static final long NIGHTS_8 = 8L;
    private static final long TOTAL_OCCUPIED_NIGHTS = 7L;
    private static final String GRANULARITY_DAY_SQL = "day";
    private static final String GRANULARITY_DAY_PARAM = "DAY";

    @Mock
    private InvoiceChargeRepository invoiceChargeRepository;

    @Mock
    private FrontdeskRoomsClient frontdeskRoomsClient;

    @InjectMocks
    private KpiReportServiceImpl kpiReportService;

    @Test
    void computesAdrRevparAndOccupancyForASingleFullyPopulatedDayBucket() {
        final RoomRevenuePeriod revenueRow = mock(RoomRevenuePeriod.class);
        when(revenueRow.getPeriodStart()).thenReturn(DAY);
        when(revenueRow.getTotalRevenue()).thenReturn(REVENUE_1000);
        when(invoiceChargeRepository.sumRoomRevenueByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(DAY.atStartOfDay()), eq(DAY_EXCLUSIVE_END.atStartOfDay()), eq(GRANULARITY_DAY_SQL)))
                .thenReturn(List.of(revenueRow));

        final OccupancyPeriodResponse occupancyRow = new OccupancyPeriodResponse(DAY, NIGHTS_8);
        when(frontdeskRoomsClient.getOccupancySummary(DAY, DAY_EXCLUSIVE_END, GRANULARITY_DAY_PARAM))
                .thenReturn(new OccupancySummaryResponse(TEN_ROOMS, List.of(occupancyRow)));

        final KpiReportDto report = kpiReportService.getKpiReport(HOTEL_ID, DAY, DAY, ReportGranularity.DAY);

        assertEquals(1, report.periods().size());
        final KpiPeriodDto period = report.periods().get(0);
        assertEquals(DAY, period.periodStart());
        assertEquals(REVENUE_1000, period.totalRoomRevenue());
        assertEquals(NIGHTS_8, period.occupiedRoomNights());
        assertEquals(TEN_ROOMS, period.availableRoomNights());
        assertEquals(new BigDecimal("125.00"), period.adr());
        assertEquals(new BigDecimal("100.00"), period.revpar());
        assertEquals(new BigDecimal("0.8000"), period.occupancyRate());
    }

    @Test
    void convertsTheInclusiveEndDateToAnExclusiveUpperBoundForBothCollaborators() {
        when(invoiceChargeRepository.sumRoomRevenueByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(DAY.atStartOfDay()), eq(DAY_EXCLUSIVE_END.atStartOfDay()), eq(GRANULARITY_DAY_SQL)))
                .thenReturn(List.of());
        when(frontdeskRoomsClient.getOccupancySummary(DAY, DAY_EXCLUSIVE_END, GRANULARITY_DAY_PARAM))
                .thenReturn(new OccupancySummaryResponse(0, List.of()));

        kpiReportService.getKpiReport(HOTEL_ID, DAY, DAY, ReportGranularity.DAY);

        verify(invoiceChargeRepository).sumRoomRevenueByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(DAY.atStartOfDay()), eq(DAY_EXCLUSIVE_END.atStartOfDay()), eq(GRANULARITY_DAY_SQL));
        verify(frontdeskRoomsClient).getOccupancySummary(DAY, DAY_EXCLUSIVE_END, GRANULARITY_DAY_PARAM);
    }

    @Test
    void outerJoinsPeriodsPresentOnOnlyOneSideDefaultingTheOtherToZero() {
        final RoomRevenuePeriod revenueOnly = mock(RoomRevenuePeriod.class);
        when(revenueOnly.getPeriodStart()).thenReturn(DAY);
        when(revenueOnly.getTotalRevenue()).thenReturn(REVENUE_1000);
        when(invoiceChargeRepository.sumRoomRevenueByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(DAY.atStartOfDay()), eq(DAY_EXCLUSIVE_END.atStartOfDay()), eq(GRANULARITY_DAY_SQL)))
                .thenReturn(List.of(revenueOnly));

        final LocalDate nightsOnlyDay = DAY.plusDays(1);
        final OccupancyPeriodResponse nightsOnly = new OccupancyPeriodResponse(nightsOnlyDay, NIGHTS_8);
        when(frontdeskRoomsClient.getOccupancySummary(DAY, DAY_EXCLUSIVE_END, GRANULARITY_DAY_PARAM))
                .thenReturn(new OccupancySummaryResponse(TEN_ROOMS, List.of(nightsOnly)));

        final KpiReportDto report = kpiReportService.getKpiReport(HOTEL_ID, DAY, DAY, ReportGranularity.DAY);

        assertEquals(2, report.periods().size());
        final KpiPeriodDto revenueOnlyPeriod = report.periods().get(0);
        assertEquals(DAY, revenueOnlyPeriod.periodStart());
        assertEquals(REVENUE_1000, revenueOnlyPeriod.totalRoomRevenue());
        assertEquals(0L, revenueOnlyPeriod.occupiedRoomNights());

        final KpiPeriodDto nightsOnlyPeriod = report.periods().get(1);
        assertEquals(nightsOnlyDay, nightsOnlyPeriod.periodStart());
        assertEquals(BigDecimal.ZERO, nightsOnlyPeriod.totalRoomRevenue());
        assertEquals(NIGHTS_8, nightsOnlyPeriod.occupiedRoomNights());
    }

    @Test
    void clipsAMonthBucketsAvailableNightsToTheActualQueryWindowNotTheFullMonth() {
        // Query window: Aug 15 (inclusive) .. Aug 20 (inclusive) = 6 days,
        // entirely within August. date_trunc('month', ...) still reports the
        // bucket as starting Aug 1 (the natural month boundary) even though
        // no data outside the query window was ever selected — the service
        // must clip the 31-day natural month length down to the 6 days that
        // actually fall inside [Aug 15, Aug 21).
        final LocalDate queryStart = LocalDate.of(2026, 8, 15);
        final LocalDate queryEnd = LocalDate.of(2026, 8, 20);
        final LocalDate queryExclusiveEnd = LocalDate.of(2026, 8, 21);
        final LocalDate monthBucketStart = LocalDate.of(2026, 8, 1);

        final RoomRevenuePeriod revenueRow = mock(RoomRevenuePeriod.class);
        when(revenueRow.getPeriodStart()).thenReturn(monthBucketStart);
        when(revenueRow.getTotalRevenue()).thenReturn(REVENUE_1000);
        when(invoiceChargeRepository.sumRoomRevenueByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(queryStart.atStartOfDay()), eq(queryExclusiveEnd.atStartOfDay()), eq("month")))
                .thenReturn(List.of(revenueRow));
        when(frontdeskRoomsClient.getOccupancySummary(queryStart, queryExclusiveEnd, "MONTH"))
                .thenReturn(new OccupancySummaryResponse(TEN_ROOMS, List.of()));

        final KpiReportDto report = kpiReportService.getKpiReport(
                HOTEL_ID, queryStart, queryEnd, ReportGranularity.MONTH);

        final KpiPeriodDto period = report.periods().get(0);
        final long expectedClippedDays = 6L;
        assertEquals(TEN_ROOMS * expectedClippedDays, period.availableRoomNights());
    }

    @Test
    void aggregatesTotalsAcrossAllPeriods() {
        final LocalDate day1 = LocalDate.of(2026, 8, 1);
        final LocalDate day2 = LocalDate.of(2026, 8, 2);
        final LocalDate exclusiveEnd = LocalDate.of(2026, 8, 3);

        final RoomRevenuePeriod row1 = mock(RoomRevenuePeriod.class);
        when(row1.getPeriodStart()).thenReturn(day1);
        when(row1.getTotalRevenue()).thenReturn(new BigDecimal("100.00"));
        final RoomRevenuePeriod row2 = mock(RoomRevenuePeriod.class);
        when(row2.getPeriodStart()).thenReturn(day2);
        when(row2.getTotalRevenue()).thenReturn(new BigDecimal("200.00"));
        when(invoiceChargeRepository.sumRoomRevenueByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(day1.atStartOfDay()), eq(exclusiveEnd.atStartOfDay()), eq(GRANULARITY_DAY_SQL)))
                .thenReturn(List.of(row1, row2));

        when(frontdeskRoomsClient.getOccupancySummary(day1, exclusiveEnd, GRANULARITY_DAY_PARAM))
                .thenReturn(new OccupancySummaryResponse(TEN_ROOMS, List.of(
                        new OccupancyPeriodResponse(day1, 3L),
                        new OccupancyPeriodResponse(day2, 4L))));

        final KpiReportDto report = kpiReportService.getKpiReport(HOTEL_ID, day1, day2, ReportGranularity.DAY);

        assertEquals(new BigDecimal("300.00"), report.totals().totalRoomRevenue());
        assertEquals(TOTAL_OCCUPIED_NIGHTS, report.totals().occupiedRoomNights());
        assertEquals(TEN_ROOMS * 2L, report.totals().availableRoomNights());
    }

    @Test
    void doesNotDivideByZeroWhenThereIsNoRevenueOrNoRooms() {
        when(invoiceChargeRepository.sumRoomRevenueByHotelIdGroupedByPeriod(
                eq(HOTEL_ID), eq(DAY.atStartOfDay()), eq(DAY_EXCLUSIVE_END.atStartOfDay()), eq(GRANULARITY_DAY_SQL)))
                .thenReturn(List.of());
        when(frontdeskRoomsClient.getOccupancySummary(DAY, DAY_EXCLUSIVE_END, GRANULARITY_DAY_PARAM))
                .thenReturn(new OccupancySummaryResponse(0, List.of()));

        final KpiReportDto report = kpiReportService.getKpiReport(HOTEL_ID, DAY, DAY, ReportGranularity.DAY);

        assertEquals(List.of(), report.periods());
        assertEquals(BigDecimal.ZERO, report.totals().adr());
        assertEquals(BigDecimal.ZERO, report.totals().revpar());
        assertEquals(BigDecimal.ZERO, report.totals().occupancyRate());
    }
}
