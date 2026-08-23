package com.hotelpms.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * RevPAR/ADR/Occupancy for one time bucket (or, as {@code totals} on
 * {@link KpiReportDto}, the whole reporting period) — epic C4.
 *
 * @param periodStart          the start of this time bucket
 * @param totalRoomRevenue     room-night revenue billed within this bucket
 * @param occupiedRoomNights   nights actually stayed (only {@code CHECKED_OUT} stays), this bucket
 * @param availableRoomNights  {@code totalRooms × days in this bucket}
 * @param adr                  average daily rate: {@code totalRoomRevenue / occupiedRoomNights}, zero if no nights sold
 * @param revpar               revenue per available room: {@code totalRoomRevenue / availableRoomNights}
 * @param occupancyRate        {@code occupiedRoomNights / availableRoomNights}, as a fraction in [0, 1]
 */
public record KpiPeriodDto(
        LocalDate periodStart,
        BigDecimal totalRoomRevenue,
        long occupiedRoomNights,
        long availableRoomNights,
        BigDecimal adr,
        BigDecimal revpar,
        BigDecimal occupancyRate) {
}
