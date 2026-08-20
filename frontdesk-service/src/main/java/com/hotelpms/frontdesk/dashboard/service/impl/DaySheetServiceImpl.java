package com.hotelpms.frontdesk.dashboard.service.impl;

import com.hotelpms.frontdesk.dashboard.dto.DaySheetResponse;
import com.hotelpms.frontdesk.dashboard.service.DaySheetService;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.rooms.repository.RoomStatusCount;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link DaySheetService}.
 *
 * <p>Composes existing repository/service methods from the rooms,
 * reservations and stays domains — all owned by this same service, so no
 * cross-service Feign calls are needed. Available-room logic is reused from
 * {@link ReservationService#getAvailableRooms} rather than duplicated here.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:DesignForExtension")
public class DaySheetServiceImpl implements DaySheetService {

    private static final Set<ReservationStatus> ARRIVAL_STATUSES =
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.PENDING);
    private static final Set<ReservationStatus> DEPARTURE_STATUSES =
            Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.PENDING);

    private final ReservationRepository reservationRepository;
    private final StayRepository stayRepository;
    private final RoomRepository roomRepository;
    private final ReservationService reservationService;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public DaySheetResponse getDaySheet(final LocalDate date, final UUID hotelId) {
        final int arrivals = reservationRepository.countByHotelIdAndCheckInDateAndStatusIn(
                hotelId, date, ARRIVAL_STATUSES);
        final int departures = reservationRepository.countByHotelIdAndCheckOutDateAndStatusIn(
                hotelId, date, DEPARTURE_STATUSES);
        final long currentStays = stayRepository.countByHotelIdAndStatus(hotelId, StayStatus.CHECKED_IN);
        final long guestsInHouse = stayRepository.countGuestsInHouseByHotelId(hotelId, StayStatus.CHECKED_IN);
        final int availableRooms = reservationService.getAvailableRooms(date, date.plusDays(1)).size();
        final Map<RoomStatus, Long> roomStatusCounts = resolveRoomStatusCounts(hotelId);

        return new DaySheetResponse(date, arrivals, departures, guestsInHouse, currentStays,
                availableRooms, roomStatusCounts);
    }

    private Map<RoomStatus, Long> resolveRoomStatusCounts(final UUID hotelId) {
        final List<RoomStatusCount> counts = roomRepository.countRoomsByStatusForHotelId(hotelId);
        final Map<RoomStatus, Long> result = new EnumMap<>(RoomStatus.class);
        for (final RoomStatusCount count : counts) {
            result.put(count.getStatus(), count.getCount());
        }
        return result;
    }
}
