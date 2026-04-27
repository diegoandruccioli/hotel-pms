package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.domain.HotelSettings;
import com.hotelpms.stay.dto.HotelSettingsRequest;
import com.hotelpms.stay.dto.HotelSettingsResponse;
import com.hotelpms.stay.repository.HotelSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelSettingsServiceImplTest {

    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    @InjectMocks
    private HotelSettingsServiceImpl hotelSettingsService;

    @Test
    void shouldReturnExistingSettingsOnGetOrCreate() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings existing = new HotelSettings();
        existing.setHotelId(hotelId);
        existing.setAlloggiatiAutoSend(true);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.of(existing));

        final HotelSettingsResponse result = hotelSettingsService.getOrCreate(hotelId);

        assertNotNull(result);
        assertEquals(hotelId, result.hotelId());
        assertTrue(result.alloggiatiAutoSend());
        verify(hotelSettingsRepository, times(1)).findById(Objects.requireNonNull(hotelId));
        verifyNoMoreInteractions(hotelSettingsRepository);
    }

    @Test
    void shouldCreateDefaultSettingsWhenNoneExist() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings saved = new HotelSettings();
        saved.setHotelId(hotelId);

        final HotelSettings expectedArg = new HotelSettings();
        expectedArg.setHotelId(hotelId);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.empty());
        when(hotelSettingsRepository.save(Objects.requireNonNull(expectedArg))).thenReturn(Objects.requireNonNull(saved));

        final HotelSettingsResponse result = hotelSettingsService.getOrCreate(hotelId);

        assertNotNull(result);
        assertEquals(hotelId, result.hotelId());
        assertFalse(result.alloggiatiAutoSend());
        verify(hotelSettingsRepository, times(1)).save(Objects.requireNonNull(expectedArg));
    }

    @Test
    void shouldUpdateExistingSettings() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings existing = new HotelSettings();
        existing.setHotelId(hotelId);
        existing.setAlloggiatiAutoSend(false);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.of(existing));
        when(hotelSettingsRepository.save(existing)).thenReturn(existing);

        final HotelSettingsResponse result =
                hotelSettingsService.update(hotelId, new HotelSettingsRequest(true));

        assertNotNull(result);
        assertTrue(Objects.requireNonNull(result).alloggiatiAutoSend());
        verify(hotelSettingsRepository, times(1)).save(existing);
    }

    @Test
    void shouldCreateAndUpdateWhenSettingsDoNotExistOnUpdate() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings created = new HotelSettings();
        created.setHotelId(hotelId);
        created.setAlloggiatiAutoSend(true);

        final HotelSettings expectedArg = new HotelSettings();
        expectedArg.setHotelId(hotelId);
        expectedArg.setAlloggiatiAutoSend(true);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.empty());
        when(hotelSettingsRepository.save(Objects.requireNonNull(expectedArg))).thenReturn(Objects.requireNonNull(created));

        final HotelSettingsResponse result =
                hotelSettingsService.update(hotelId, new HotelSettingsRequest(true));

        assertNotNull(result);
        assertTrue(result.alloggiatiAutoSend());
        verify(hotelSettingsRepository, times(1)).save(Objects.requireNonNull(expectedArg));
    }
}
