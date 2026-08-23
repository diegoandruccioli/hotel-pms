package com.hotelpms.billing.dto;

import java.util.List;

/**
 * RevPAR/ADR/Occupancy trend for a date range plus the whole-period totals,
 * scoped to the caller's hotel — epic C4.
 *
 * @param periods one entry per time bucket, ordered by {@code periodStart}
 * @param totals  the same shape, aggregated over the entire requested range
 */
public record KpiReportDto(List<KpiPeriodDto> periods, KpiPeriodDto totals) {

    /**
     * Defensive copy — a caller must not be able to mutate the list backing
     * this response after construction.
     *
     * @param periods one entry per time bucket
     * @param totals  the whole-period aggregate
     */
    public KpiReportDto {
        periods = List.copyOf(periods);
    }
}
