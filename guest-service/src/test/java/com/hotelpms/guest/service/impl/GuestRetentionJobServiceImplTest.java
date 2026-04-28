package com.hotelpms.guest.service.impl;

import com.hotelpms.guest.client.BillingServiceClient;
import com.hotelpms.guest.client.StayServiceClient;
import com.hotelpms.guest.client.dto.GuestInvoiceClientResponse;
import com.hotelpms.guest.client.dto.GuestLastStayClientResponse;
import com.hotelpms.guest.model.Guest;
import com.hotelpms.guest.model.GuestPrivacySettings;
import com.hotelpms.guest.repository.GuestPrivacySettingsRepository;
import com.hotelpms.guest.repository.GuestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestRetentionJobServiceImplTest {

    @Mock private GuestRepository guestRepository;
    @Mock private GuestPrivacySettingsRepository settingsRepository;
    @Mock private StayServiceClient stayServiceClient;
    @Mock private BillingServiceClient billingServiceClient;

    @InjectMocks
    private GuestRetentionJobServiceImpl job;

    private Guest buildGuest(final UUID hotelId) {
        return Guest.builder()
                .id(UUID.randomUUID())
                .hotelId(hotelId)
                .firstName("Mario")
                .lastName("Rossi")
                .email("m@t.com")
                .identityDocuments(new ArrayList<>())
                .active(true)
                .gdprConsentDate(LocalDate.now().minusYears(11))
                .build();
    }

    @Test
    void shouldAnonymiseGuestWithBothHoldsExpired() {
        final UUID hotelId = UUID.randomUUID();
        final Guest guest  = buildGuest(hotelId);
        final UUID guestId = Objects.requireNonNull(guest.getId());
        final GuestPrivacySettings settings = GuestPrivacySettings.builder()
                .hotelId(hotelId).guestRetentionYears(5).build();

        when(guestRepository.findByGdprConsentDateBefore(any(LocalDate.class)))
                .thenReturn(List.of(guest));
        when(settingsRepository.findById(Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(settings));
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true,
                        LocalDate.now().minusYears(6)));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(true,
                        LocalDate.now().minusYears(11)));
        when(guestRepository.save(any(Guest.class))).thenReturn(guest);

        job.runRetentionJob();

        assertFalse(guest.isActive());
        assertEquals("GDPR", guest.getFirstName());
        verify(guestRepository, times(1)).save(guest);
    }

    @Test
    void shouldSkipGuestWithActiveTulpsHold() {
        final UUID hotelId = UUID.randomUUID();
        final Guest guest  = buildGuest(hotelId);
        final UUID guestId = Objects.requireNonNull(guest.getId());
        final GuestPrivacySettings settings = GuestPrivacySettings.builder()
                .hotelId(hotelId).guestRetentionYears(5).build();

        when(guestRepository.findByGdprConsentDateBefore(any(LocalDate.class)))
                .thenReturn(List.of(guest));
        when(settingsRepository.findById(Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(settings));
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true,
                        LocalDate.now().minusYears(3)));

        job.runRetentionJob();

        verify(guestRepository, never()).save(any());
    }

    @Test
    void shouldSkipGuestWithActiveFiscalHold() {
        final UUID hotelId = UUID.randomUUID();
        final Guest guest  = buildGuest(hotelId);
        final UUID guestId = Objects.requireNonNull(guest.getId());
        final GuestPrivacySettings settings = GuestPrivacySettings.builder()
                .hotelId(hotelId).guestRetentionYears(5).build();

        when(guestRepository.findByGdprConsentDateBefore(any(LocalDate.class)))
                .thenReturn(List.of(guest));
        when(settingsRepository.findById(Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(settings));
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true,
                        LocalDate.now().minusYears(6)));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(true,
                        LocalDate.now().minusYears(8)));

        job.runRetentionJob();

        verify(guestRepository, never()).save(any());
    }

    @Test
    void shouldSkipGuestWithNoStaysNorInvoicesAndUseConservativeFiscalCutoff() {
        final UUID hotelId = UUID.randomUUID();
        final Guest guestNoHistory = Guest.builder()
                .id(UUID.randomUUID()).hotelId(hotelId)
                .firstName("Anna").lastName("Verdi").email("a@v.com")
                .identityDocuments(new ArrayList<>()).active(true)
                .gdprConsentDate(LocalDate.now().minusYears(11))
                .build();
        final UUID guestId = Objects.requireNonNull(guestNoHistory.getId());
        final GuestPrivacySettings settings = GuestPrivacySettings.builder()
                .hotelId(hotelId).guestRetentionYears(5).build();

        when(guestRepository.findByGdprConsentDateBefore(any(LocalDate.class)))
                .thenReturn(List.of(guestNoHistory));
        when(settingsRepository.findById(Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(settings));
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(false, null));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(false, null));
        when(guestRepository.save(any(Guest.class))).thenReturn(guestNoHistory);

        job.runRetentionJob();

        assertFalse(guestNoHistory.isActive());
        verify(guestRepository, times(1)).save(guestNoHistory);
    }

    @Test
    void shouldDoNothingWhenNoCandidatesFound() {
        when(guestRepository.findByGdprConsentDateBefore(any(LocalDate.class)))
                .thenReturn(List.of());

        job.runRetentionJob();

        verify(guestRepository, never()).save(any());
        verify(stayServiceClient, never()).getLastStayDate(any());
        verify(billingServiceClient, never()).getLastInvoiceDate(any());
    }
}
