package com.hotelpms.frontdesk.dashboard.service.impl;

import com.hotelpms.frontdesk.dashboard.dto.OccupancyPeriodResponse;
import com.hotelpms.frontdesk.dashboard.dto.OccupancySummaryResponse;
import com.hotelpms.frontdesk.dashboard.dto.ReportGranularity;
import com.hotelpms.frontdesk.dashboard.service.OccupancySummaryService;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.stays.repository.StayOccupancyPeriod;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link OccupancySummaryService}.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:DesignForExtension")
public class OccupancySummaryServiceImpl implements OccupancySummaryService {

    private final RoomRepository roomRepository;
    private final StayRepository stayRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public OccupancySummaryResponse getOccupancySummary(
            final LocalDate dateFrom, final LocalDate dateTo,
            final ReportGranularity granularity, final UUID hotelId) {
        final int totalRooms = (int) roomRepository.countByActiveTrueAndHotelId(hotelId);
        final List<StayOccupancyPeriod> rows = stayRepository.sumOccupiedRoomNightsByHotelIdGroupedByPeriod(
                hotelId, dateFrom.atStartOfDay(), dateTo.atStartOfDay(), granularity.sqlValue());

        final List<OccupancyPeriodResponse> periods = rows.stream()
                .map(row -> new OccupancyPeriodResponse(row.getPeriodStart(), row.getOccupiedRoomNights()))
                .toList();

        return new OccupancySummaryResponse(totalRooms, periods);
    }
}
