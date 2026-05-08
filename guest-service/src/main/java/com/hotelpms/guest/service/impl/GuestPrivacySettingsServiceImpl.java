package com.hotelpms.guest.service.impl;

import com.hotelpms.guest.dto.request.GuestPrivacySettingsRequest;
import com.hotelpms.guest.dto.response.GuestPrivacySettingsResponse;
import com.hotelpms.guest.model.GuestPrivacySettings;
import com.hotelpms.guest.repository.GuestPrivacySettingsRepository;
import com.hotelpms.guest.service.GuestPrivacySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link GuestPrivacySettingsService}.
 */
@Service
@RequiredArgsConstructor
public class GuestPrivacySettingsServiceImpl implements GuestPrivacySettingsService {

    private final GuestPrivacySettingsRepository repository;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public GuestPrivacySettingsResponse getOrCreate(final UUID hotelId) {
        return toResponse(getOrCreateEntity(hotelId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public GuestPrivacySettingsResponse update(final UUID hotelId,
                                                final GuestPrivacySettingsRequest request) {
        final GuestPrivacySettings settings = getOrCreateEntity(hotelId);
        settings.setGuestRetentionYears(request.guestRetentionYears());
        return toResponse(repository.save(Objects.requireNonNull(settings)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public GuestPrivacySettings getOrCreateEntity(final UUID hotelId) {
        return repository.findById(Objects.requireNonNull(hotelId))
                .orElseGet(() -> repository.save(
                        Objects.requireNonNull(buildDefault(hotelId))));
    }

    private static GuestPrivacySettings buildDefault(final UUID hotelId) {
        return GuestPrivacySettings.builder()
                .hotelId(hotelId)
                .guestRetentionYears(GuestPrivacySettings.TULPS_MIN_YEARS)
                .build();
    }

    private static GuestPrivacySettingsResponse toResponse(final GuestPrivacySettings entity) {
        return new GuestPrivacySettingsResponse(
                entity.getHotelId(),
                entity.getGuestRetentionYears(),
                GuestPrivacySettings.TULPS_MIN_YEARS,
                GuestPrivacySettings.FISCAL_MIN_YEARS);
    }
}
