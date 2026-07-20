package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsRequest;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.repository.AlloggiatiComuneRepository;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import com.hotelpms.frontdesk.stays.security.AlloggiatiCredentialEncryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class HotelSettingsServiceImplTest {

    private static final String HOTEL_USERNAME = "hotelUser";
    private static final String ENCRYPTED_PASSWORD = "enc-pass";
    private static final String ENCRYPTED_WS_KEY = "enc-key";
    private static final String COMUNE_ROMA = "Roma";
    private static final String PROVINCIA_RM = "RM";

    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    @Mock
    private AlloggiatiCredentialEncryptor alloggiatiCredentialEncryptor;

    @Mock
    private AlloggiatiComuneRepository alloggiatiComuneRepository;

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

        final HotelSettingsResponse result = hotelSettingsService.update(
                hotelId, new HotelSettingsRequest(true, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null));

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

        final HotelSettingsResponse result = hotelSettingsService.update(
                hotelId, new HotelSettingsRequest(true, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null));

        assertNotNull(result);
        assertTrue(result.alloggiatiAutoSend());
        verify(hotelSettingsRepository, times(1)).save(Objects.requireNonNull(expectedArg));
    }

    @Test
    void shouldEncryptAndStoreAlloggiatiCredentialsWhenProvided() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings existing = new HotelSettings();
        existing.setHotelId(hotelId);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.of(existing));
        when(hotelSettingsRepository.save(existing)).thenReturn(existing);
        when(alloggiatiCredentialEncryptor.encrypt("plainPass")).thenReturn(ENCRYPTED_PASSWORD);
        when(alloggiatiCredentialEncryptor.encrypt("plainKey")).thenReturn(ENCRYPTED_WS_KEY);

        final HotelSettingsResponse result = hotelSettingsService.update(hotelId, new HotelSettingsRequest(
                false, null, null, null, null, null, HOTEL_USERNAME, "plainPass", "plainKey",
                null, null, null, null, null, null, null, null));

        assertEquals(HOTEL_USERNAME, existing.getAlloggiatiUsername());
        assertEquals(ENCRYPTED_PASSWORD, existing.getAlloggiatiPasswordEncrypted());
        assertEquals(ENCRYPTED_WS_KEY, existing.getAlloggiatiWsKeyEncrypted());
        assertTrue(result.alloggiatiCredentialsConfigured());
    }

    @Test
    void shouldKeepExistingEncryptedCredentialsWhenPasswordAndWsKeyAreBlankOnUpdate() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings existing = new HotelSettings();
        existing.setHotelId(hotelId);
        existing.setAlloggiatiUsername(HOTEL_USERNAME);
        existing.setAlloggiatiPasswordEncrypted(ENCRYPTED_PASSWORD);
        existing.setAlloggiatiWsKeyEncrypted(ENCRYPTED_WS_KEY);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.of(existing));
        when(hotelSettingsRepository.save(existing)).thenReturn(existing);

        // Admin only updates hotelName — password/WsKey fields are left blank in the
        // UI (write-only), so the previously stored encrypted values must survive.
        final HotelSettingsResponse result = hotelSettingsService.update(hotelId, new HotelSettingsRequest(
                false, "New Name", null, null, null, null, HOTEL_USERNAME, "", "",
                null, null, null, null, null, null, null, null));

        assertEquals(ENCRYPTED_PASSWORD, existing.getAlloggiatiPasswordEncrypted());
        assertEquals(ENCRYPTED_WS_KEY, existing.getAlloggiatiWsKeyEncrypted());
        assertTrue(result.alloggiatiCredentialsConfigured());
        verify(alloggiatiCredentialEncryptor, times(0)).encrypt(ArgumentMatchers.any());
    }

    @Test
    void shouldNotWipeOtherFieldsWhenPatchingOnlyOneBooleanToggle() {
        // Regression test: a partial update sending only a single toggle (the pattern
        // used by SettingsSystem.tsx) must never null out the rest of the hotel
        // profile — null fields in the request mean "unchanged", not "clear".
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings existing = new HotelSettings();
        existing.setHotelId(hotelId);
        existing.setHotelName("Hotel Bella Vista");
        existing.setAddress("Via Roma 12");
        existing.setAlloggiatiAutoSend(false);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.of(existing));
        when(hotelSettingsRepository.save(existing)).thenReturn(existing);

        // Only alloggiatiAutoSend is set; every other field (including the new email
        // toggles) is null/absent, mirroring a single-switch PUT from the frontend.
        final HotelSettingsResponse result = hotelSettingsService.update(
                hotelId, new HotelSettingsRequest(true, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null));

        assertTrue(result.alloggiatiAutoSend());
        assertEquals("Hotel Bella Vista", result.hotelName());
        assertEquals("Via Roma 12", result.address());
        assertTrue(result.sendReservationConfirmedEmail());
        assertTrue(result.sendCheckoutEmail());
    }

    @Test
    void shouldApplyEmailNotificationSettingsOnUpdate() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings existing = new HotelSettings();
        existing.setHotelId(hotelId);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.of(existing));
        when(hotelSettingsRepository.save(existing)).thenReturn(existing);

        final HotelSettingsResponse result = hotelSettingsService.update(hotelId, new HotelSettingsRequest(
                null, null, null, null, null, null, null, null, null,
                false, false, "Oggetto custom", "Oggetto checkout custom", "A presto!", null, null, null));

        assertFalse(result.sendReservationConfirmedEmail());
        assertFalse(result.sendCheckoutEmail());
        assertEquals("Oggetto custom", result.emailSubjectReservationConfirmed());
        assertEquals("Oggetto checkout custom", result.emailSubjectCheckout());
        assertEquals("A presto!", result.emailGreetingText());
    }

    @Test
    void shouldStoreCapComuneProvinciaWhenComuneIsValid() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings existing = new HotelSettings();
        existing.setHotelId(hotelId);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.of(existing));
        when(hotelSettingsRepository.save(existing)).thenReturn(existing);
        when(alloggiatiComuneRepository.existsActiveByComuneAndProvincia(
                COMUNE_ROMA, PROVINCIA_RM, LocalDate.now())).thenReturn(true);

        final HotelSettingsResponse result = hotelSettingsService.update(hotelId, new HotelSettingsRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "00100", COMUNE_ROMA, PROVINCIA_RM));

        assertEquals("00100", result.cap());
        assertEquals(COMUNE_ROMA, result.comune());
        assertEquals(PROVINCIA_RM, result.provincia());
    }

    @Test
    void shouldRejectComuneNotMatchingAnyRealMunicipality() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings existing = new HotelSettings();
        existing.setHotelId(hotelId);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.of(existing));
        when(alloggiatiComuneRepository.existsActiveByComuneAndProvincia(
                "Cittainesistente", PROVINCIA_RM, LocalDate.now())).thenReturn(false);

        final HotelSettingsRequest request = new HotelSettingsRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, "Cittainesistente", PROVINCIA_RM);

        assertThrows(BadRequestException.class, () -> hotelSettingsService.update(hotelId, request));
        verify(hotelSettingsRepository, times(0)).save(ArgumentMatchers.any());
    }

    @Test
    void shouldRejectComuneWithoutProvincia() {
        final UUID hotelId = UUID.randomUUID();
        final HotelSettings existing = new HotelSettings();
        existing.setHotelId(hotelId);

        when(hotelSettingsRepository.findById(Objects.requireNonNull(hotelId))).thenReturn(Optional.of(existing));

        final HotelSettingsRequest request = new HotelSettingsRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, COMUNE_ROMA, null);

        assertThrows(BadRequestException.class, () -> hotelSettingsService.update(hotelId, request));
    }
}
