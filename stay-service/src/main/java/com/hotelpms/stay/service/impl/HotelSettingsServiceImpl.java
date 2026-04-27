package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.domain.HotelSettings;
import com.hotelpms.stay.dto.HotelSettingsRequest;
import com.hotelpms.stay.dto.HotelSettingsResponse;
import com.hotelpms.stay.repository.HotelSettingsRepository;
import com.hotelpms.stay.service.HotelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link HotelSettingsService}.
 */
@Service
@RequiredArgsConstructor
public class HotelSettingsServiceImpl implements HotelSettingsService {

    private final HotelSettingsRepository hotelSettingsRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public HotelSettingsResponse getOrCreate(final UUID hotelId) {
        return hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))
                .map(this::toResponse)
                .orElseGet(() -> toResponse(createDefault(hotelId)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public HotelSettingsResponse update(final UUID hotelId, final HotelSettingsRequest request) {
        final HotelSettings settings = hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))
                .orElseGet(() -> buildDefault(hotelId));
        settings.setAlloggiatiAutoSend(request.alloggiatiAutoSend());
        return toResponse(hotelSettingsRepository.save(settings));
    }

    private HotelSettings createDefault(final UUID hotelId) {
        return hotelSettingsRepository.save(Objects.requireNonNull(buildDefault(hotelId)));
    }

    private static HotelSettings buildDefault(final UUID hotelId) {
        final HotelSettings defaults = new HotelSettings();
        defaults.setHotelId(hotelId);
        return defaults;
    }

    private HotelSettingsResponse toResponse(final HotelSettings entity) {
        return new HotelSettingsResponse(entity.getHotelId(), entity.isAlloggiatiAutoSend());
    }
}
