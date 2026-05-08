package com.hotelpms.guest.service.impl;

import com.hotelpms.guest.dto.request.GuestPrivacySettingsRequest;
import com.hotelpms.guest.dto.response.GuestPrivacySettingsResponse;
import com.hotelpms.guest.model.GuestPrivacySettings;
import com.hotelpms.guest.repository.GuestPrivacySettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestPrivacySettingsServiceImplTest {

    private static final int RETENTION_YEARS_7 = 7;
    private static final int RETENTION_YEARS_6 = 6;

    @Mock
    private GuestPrivacySettingsRepository repository;

    @InjectMocks
    private GuestPrivacySettingsServiceImpl service;

    @Test
    void shouldReturnExistingSettings() {
        final UUID hotelId = UUID.randomUUID();
        final GuestPrivacySettings existing = GuestPrivacySettings.builder()
                .hotelId(hotelId)
                .guestRetentionYears(RETENTION_YEARS_7)
                .build();

        when(repository.findById(Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(existing));

        final GuestPrivacySettingsResponse result = service.getOrCreate(hotelId);

        assertNotNull(result);
        assertEquals(hotelId, result.hotelId());
        assertEquals(RETENTION_YEARS_7, result.guestRetentionYears());
        assertEquals(GuestPrivacySettings.TULPS_MIN_YEARS, result.tulpsMinYears());
        assertEquals(GuestPrivacySettings.FISCAL_MIN_YEARS, result.fiscalMinYears());
        verify(repository, times(1)).findById(Objects.requireNonNull(hotelId));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldCreateDefaultWhenNoneExist() {
        final UUID hotelId = UUID.randomUUID();
        final GuestPrivacySettings defaultSettings = GuestPrivacySettings.builder()
                .hotelId(hotelId)
                .guestRetentionYears(GuestPrivacySettings.TULPS_MIN_YEARS)
                .build();

        when(repository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.empty());
        when(repository.save(Objects.requireNonNull(defaultSettings)))
                .thenReturn(Objects.requireNonNull(defaultSettings));

        final GuestPrivacySettingsResponse result = service.getOrCreate(hotelId);

        assertNotNull(result);
        assertEquals(hotelId, result.hotelId());
        assertEquals(GuestPrivacySettings.TULPS_MIN_YEARS, result.guestRetentionYears());
        verify(repository, times(1)).save(Objects.requireNonNull(defaultSettings));
    }

    @Test
    void shouldUpdateRetentionYears() {
        final UUID hotelId = UUID.randomUUID();
        final GuestPrivacySettings existing = GuestPrivacySettings.builder()
                .hotelId(hotelId)
                .guestRetentionYears(GuestPrivacySettings.TULPS_MIN_YEARS)
                .build();

        when(repository.findById(Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(existing));
        when(repository.save(Objects.requireNonNull(existing)))
                .thenReturn(Objects.requireNonNull(existing));

        final GuestPrivacySettingsResponse result =
                service.update(hotelId, new GuestPrivacySettingsRequest(RETENTION_YEARS_7));

        assertNotNull(result);
        assertEquals(RETENTION_YEARS_7, result.guestRetentionYears());
        verify(repository, times(1)).save(Objects.requireNonNull(existing));
    }

    @Test
    void shouldCreateAndUpdateWhenNoneExistOnUpdate() {
        final UUID hotelId = UUID.randomUUID();
        final GuestPrivacySettings toSave = GuestPrivacySettings.builder()
                .hotelId(hotelId)
                .guestRetentionYears(GuestPrivacySettings.TULPS_MIN_YEARS)
                .build();
        final GuestPrivacySettings saved = GuestPrivacySettings.builder()
                .hotelId(hotelId)
                .guestRetentionYears(RETENTION_YEARS_6)
                .build();

        when(repository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.empty());
        when(repository.save(Objects.requireNonNull(toSave)))
                .thenReturn(Objects.requireNonNull(saved));
        when(repository.save(Objects.requireNonNull(saved)))
                .thenReturn(Objects.requireNonNull(saved));

        final GuestPrivacySettingsResponse result =
                service.update(hotelId, new GuestPrivacySettingsRequest(RETENTION_YEARS_6));

        assertNotNull(result);
        verify(repository).save(Objects.requireNonNull(toSave));
        verify(repository).save(Objects.requireNonNull(saved));
    }

    @Test
    void shouldNotCallSaveWhenSettingsAlreadyExistOnGetOrCreate() {
        final UUID hotelId = UUID.randomUUID();
        final GuestPrivacySettings existing = GuestPrivacySettings.builder()
                .hotelId(hotelId).guestRetentionYears(GuestPrivacySettings.TULPS_MIN_YEARS).build();

        when(repository.findById(Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(existing));

        service.getOrCreate(hotelId);

        verify(repository, times(1)).findById(Objects.requireNonNull(hotelId));
        verifyNoMoreInteractions(repository);
    }
}
