package com.hotelpms.frontdesk.pricing.service.impl;

import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import com.hotelpms.frontdesk.pricing.dto.NightlyRate;
import com.hotelpms.frontdesk.pricing.repository.RateSeasonRepository;
import com.hotelpms.frontdesk.pricing.service.RatePricingService;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link RatePricingService}: looks up an active
 * {@link RateSeason} covering the requested date, falling back to
 * {@code RoomType.basePrice} (via the already-cached {@link RoomTypeService})
 * when none covers it.
 */
@Service
@RequiredArgsConstructor
class RatePricingServiceImpl implements RatePricingService {

    private static final String ROOM_TYPE_ID_NULL_MSG = "RoomType ID cannot be null";
    private static final String HOTEL_ID_NULL_MSG = "Hotel ID cannot be null";
    private static final String DATE_NULL_MSG = "Date cannot be null";

    private final RateSeasonRepository rateSeasonRepository;
    private final RoomTypeService roomTypeService;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public NightlyRate resolveNightlyRate(final UUID roomTypeId, final UUID hotelId, final LocalDate date) {
        Objects.requireNonNull(roomTypeId, ROOM_TYPE_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        Objects.requireNonNull(date, DATE_NULL_MSG);

        return rateSeasonRepository.findCovering(roomTypeId, hotelId, date)
                .map(season -> new NightlyRate(date, season.getNightlyPrice(), season.getId()))
                .orElseGet(() -> fallbackToBasePrice(roomTypeId, hotelId, date));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<NightlyRate> resolveStayRates(
            final UUID roomTypeId, final UUID hotelId, final LocalDate checkIn, final LocalDate checkOutExclusive) {
        Objects.requireNonNull(roomTypeId, ROOM_TYPE_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        Objects.requireNonNull(checkIn, "Check-in date cannot be null");
        Objects.requireNonNull(checkOutExclusive, "Check-out date cannot be null");

        final List<NightlyRate> rates = new ArrayList<>();
        for (LocalDate night = checkIn; night.isBefore(checkOutExclusive); night = night.plusDays(1)) {
            rates.add(resolveNightlyRate(roomTypeId, hotelId, night));
        }
        return rates;
    }

    private NightlyRate fallbackToBasePrice(final UUID roomTypeId, final UUID hotelId, final LocalDate date) {
        final RoomTypeResponse roomType = roomTypeService.getRoomTypeById(roomTypeId, hotelId);
        return new NightlyRate(date, roomType.basePrice(), null);
    }
}
