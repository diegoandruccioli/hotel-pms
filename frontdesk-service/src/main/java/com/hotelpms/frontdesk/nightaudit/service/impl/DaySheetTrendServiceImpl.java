package com.hotelpms.frontdesk.nightaudit.service.impl;

import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditRun;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditStatus;
import com.hotelpms.frontdesk.nightaudit.dto.DaySheetTrendPoint;
import com.hotelpms.frontdesk.nightaudit.dto.DaySheetTrendResponse;
import com.hotelpms.frontdesk.nightaudit.repository.NightAuditRunRepository;
import com.hotelpms.frontdesk.nightaudit.service.DaySheetTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link DaySheetTrendService}, backed by the completed rows of
 * {@code night_audit_runs}.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:DesignForExtension")
public class DaySheetTrendServiceImpl implements DaySheetTrendService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 14;

    private final NightAuditRunRepository nightAuditRunRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public DaySheetTrendResponse getTrend(final LocalDate date, final int days, final UUID hotelId) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BadRequestException("DAY_SHEET_TREND_DAYS_OUT_OF_RANGE");
        }
        final LocalDate from = date.minusDays(days);
        final LocalDate to = date.minusDays(1);
        final List<DaySheetTrendPoint> points = nightAuditRunRepository
                .findByHotelIdAndStatusAndBusinessDateBetweenOrderByBusinessDateAsc(
                        hotelId, NightAuditStatus.COMPLETED, from, to)
                .stream()
                .map(DaySheetTrendServiceImpl::toPoint)
                .flatMap(Optional::stream)
                .toList();
        return new DaySheetTrendResponse(from, to, points);
    }

    // A completed run whose snapshot columns were never filled is not a data point.
    private static Optional<DaySheetTrendPoint> toPoint(final NightAuditRun run) {
        if (run.getArrivals() == null || run.getDepartures() == null
                || run.getGuestsInHouse() == null || run.getAvailableRooms() == null) {
            return Optional.empty();
        }
        return Optional.of(new DaySheetTrendPoint(
                run.getBusinessDate(),
                run.getArrivals(),
                run.getDepartures(),
                run.getGuestsInHouse(),
                run.getAvailableRooms()));
    }
}
