package com.hotelpms.guest.service.impl;

import com.hotelpms.guest.client.BillingServiceClient;
import com.hotelpms.guest.client.ReservationClient;
import com.hotelpms.guest.client.StayServiceClient;
import com.hotelpms.guest.client.dto.GuestInvoiceClientResponse;
import com.hotelpms.guest.client.dto.GuestLastStayClientResponse;
import com.hotelpms.guest.exception.GdprLegalHoldException;
import com.hotelpms.guest.mapper.GuestMapper;
import com.hotelpms.guest.mapper.IdentityDocumentMapper;
import com.hotelpms.guest.model.Guest;
import com.hotelpms.guest.model.GuestPrivacySettings;
import com.hotelpms.guest.repository.GuestRepository;
import com.hotelpms.guest.repository.IdentityDocumentRepository;
import com.hotelpms.guest.service.GuestPrivacySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Focused test suite for the GDPR legal-hold guard in
 * {@link GuestServiceImpl#deleteGuest(UUID)} (T-GST-05).
 *
 * <p>Boundary-value tests verify the exact boundary dates for both legal holds:
 * <ul>
 *   <li>TULPS: last stay exactly 5 years ago → still blocked</li>
 *   <li>TULPS: last stay 5 years and 1 day ago → cleared</li>
 *   <li>Fiscal: last invoice exactly 10 years ago → still blocked</li>
 *   <li>Fiscal: last invoice 10 years and 1 day ago → cleared</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GuestServiceImplDeleteGuardTest {

    private static final int YEARS_4 = 4;
    private static final int YEARS_5 = 5;
    private static final int YEARS_6 = 6;
    private static final int YEARS_7 = 7;
    private static final int YEARS_9 = 9;
    private static final int YEARS_10 = 10;
    private static final int DAYS_364 = 364;

    @Mock private GuestRepository guestRepository;
    @Mock private IdentityDocumentRepository identityDocumentRepository;
    @Mock private GuestMapper guestMapper;
    @Mock private IdentityDocumentMapper identityDocumentMapper;
    @Mock private ReservationClient reservationClient;
    @Mock private StayServiceClient stayServiceClient;
    @Mock private BillingServiceClient billingServiceClient;
    @Mock private GuestPrivacySettingsService privacySettingsService;

    @InjectMocks
    private GuestServiceImpl service;

    private UUID guestId;
    private UUID hotelId;
    private Guest guest;
    private GuestPrivacySettings defaultSettings;

    @BeforeEach
    void setUp() {
        guestId = UUID.randomUUID();
        hotelId = UUID.randomUUID();
        guest = Guest.builder()
                .id(guestId)
                .hotelId(hotelId)
                .firstName("Mario")
                .lastName("Rossi")
                .email("mario@test.com")
                .identityDocuments(new ArrayList<>())
                .active(true)
                .gdprConsentDate(LocalDate.now().minusYears(YEARS_6))
                .build();
        defaultSettings = GuestPrivacySettings.builder()
                .hotelId(hotelId)
                .guestRetentionYears(GuestPrivacySettings.TULPS_MIN_YEARS)
                .build();

        final UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("admin", null);
        auth.setDetails(hotelId.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void shouldAnonymiseGuestWhenNoLegalHoldsActive() {
        when(guestRepository.findByIdAndHotelId(
                Objects.requireNonNull(guestId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(guest));
        when(reservationClient.hasActiveReservations(guestId)).thenReturn(false);
        when(privacySettingsService.getOrCreateEntity(hotelId)).thenReturn(defaultSettings);
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(false, null));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(false, null));
        when(guestRepository.save(Objects.requireNonNull(guest)))
                .thenReturn(Objects.requireNonNull(guest));

        service.deleteGuest(guestId);

        assertEquals("GDPR", guest.getFirstName());
        assertFalse(guest.isActive());
    }

    @Test
    void shouldThrow451WhenTulpsHoldActive4years364days() {
        final LocalDate lastStay = LocalDate.now().minusYears(YEARS_4).minusDays(DAYS_364);
        when(guestRepository.findByIdAndHotelId(
                Objects.requireNonNull(guestId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(guest));
        when(reservationClient.hasActiveReservations(guestId)).thenReturn(false);
        when(privacySettingsService.getOrCreateEntity(hotelId)).thenReturn(defaultSettings);
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true, lastStay));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(false, null));

        final GdprLegalHoldException ex = assertThrows(
                GdprLegalHoldException.class, () -> service.deleteGuest(guestId));

        assertEquals(GdprLegalHoldException.LegalBasis.TULPS, ex.getLegalBasis());
        assertEquals(lastStay.plusYears(GuestPrivacySettings.TULPS_MIN_YEARS), ex.getUnlocksAt());
    }

    @Test
    void shouldThrow451WhenTulpsHoldActiveExactlyAtBoundary5Years() {
        final LocalDate lastStay = LocalDate.now().minusYears(YEARS_5);
        when(guestRepository.findByIdAndHotelId(
                Objects.requireNonNull(guestId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(guest));
        when(reservationClient.hasActiveReservations(guestId)).thenReturn(false);
        when(privacySettingsService.getOrCreateEntity(hotelId)).thenReturn(defaultSettings);
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true, lastStay));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(false, null));

        final GdprLegalHoldException ex = assertThrows(
                GdprLegalHoldException.class, () -> service.deleteGuest(guestId));

        assertEquals(GdprLegalHoldException.LegalBasis.TULPS, ex.getLegalBasis());
    }

    @Test
    void shouldClearTulpsWhenLastStayIs5YearsAnd1DayAgo() {
        final LocalDate lastStay = LocalDate.now().minusYears(YEARS_5).minusDays(1);
        when(guestRepository.findByIdAndHotelId(
                Objects.requireNonNull(guestId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(guest));
        when(reservationClient.hasActiveReservations(guestId)).thenReturn(false);
        when(privacySettingsService.getOrCreateEntity(hotelId)).thenReturn(defaultSettings);
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true, lastStay));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(false, null));
        when(guestRepository.save(Objects.requireNonNull(guest)))
                .thenReturn(Objects.requireNonNull(guest));

        service.deleteGuest(guestId);

        assertFalse(guest.isActive());
    }

    @Test
    void shouldThrow451WhenFiscalHoldActiveExactlyAtBoundary10Years() {
        final LocalDate lastStay = LocalDate.now().minusYears(YEARS_6);
        final LocalDate lastInvoice = LocalDate.now().minusYears(YEARS_10);
        when(guestRepository.findByIdAndHotelId(
                Objects.requireNonNull(guestId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(guest));
        when(reservationClient.hasActiveReservations(guestId)).thenReturn(false);
        when(privacySettingsService.getOrCreateEntity(hotelId)).thenReturn(defaultSettings);
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true, lastStay));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(true, lastInvoice));

        final GdprLegalHoldException ex = assertThrows(
                GdprLegalHoldException.class, () -> service.deleteGuest(guestId));

        assertEquals(GdprLegalHoldException.LegalBasis.FISCAL, ex.getLegalBasis());
        assertEquals(lastInvoice.plusYears(GuestPrivacySettings.FISCAL_MIN_YEARS),
                ex.getUnlocksAt());
    }

    @Test
    void shouldClearFiscalWhenLastInvoiceIs10YearsAnd1DayAgo() {
        final LocalDate lastStay = LocalDate.now().minusYears(YEARS_6);
        final LocalDate lastInvoice = LocalDate.now().minusYears(YEARS_10).minusDays(1);
        when(guestRepository.findByIdAndHotelId(
                Objects.requireNonNull(guestId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(guest));
        when(reservationClient.hasActiveReservations(guestId)).thenReturn(false);
        when(privacySettingsService.getOrCreateEntity(hotelId)).thenReturn(defaultSettings);
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true, lastStay));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(true, lastInvoice));
        when(guestRepository.save(Objects.requireNonNull(guest)))
                .thenReturn(Objects.requireNonNull(guest));

        service.deleteGuest(guestId);

        assertFalse(guest.isActive());
    }

    @Test
    void shouldThrow451WithBothHoldsAndReturnLatestUnlockDate() {
        final LocalDate lastStay = LocalDate.now().minusYears(YEARS_4);
        final LocalDate lastInvoice = LocalDate.now().minusYears(YEARS_9);
        when(guestRepository.findByIdAndHotelId(
                Objects.requireNonNull(guestId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(guest));
        when(reservationClient.hasActiveReservations(guestId)).thenReturn(false);
        when(privacySettingsService.getOrCreateEntity(hotelId)).thenReturn(defaultSettings);
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true, lastStay));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(true, lastInvoice));

        final GdprLegalHoldException ex = assertThrows(
                GdprLegalHoldException.class, () -> service.deleteGuest(guestId));

        assertEquals(GdprLegalHoldException.LegalBasis.TULPS_AND_FISCAL, ex.getLegalBasis());
        final LocalDate tulpsExpiry = lastStay.plusYears(GuestPrivacySettings.TULPS_MIN_YEARS);
        final LocalDate fiscalExpiry = lastInvoice.plusYears(GuestPrivacySettings.FISCAL_MIN_YEARS);
        assertEquals(fiscalExpiry.isAfter(tulpsExpiry) ? fiscalExpiry : tulpsExpiry,
                ex.getUnlocksAt());
    }

    @Test
    void shouldRespectCustomRetentionYearsFromHotelSettings() {
        final LocalDate lastStay = LocalDate.now().minusYears(YEARS_6);
        final GuestPrivacySettings customSettings = GuestPrivacySettings.builder()
                .hotelId(hotelId).guestRetentionYears(YEARS_7).build();

        when(guestRepository.findByIdAndHotelId(
                Objects.requireNonNull(guestId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(guest));
        when(reservationClient.hasActiveReservations(guestId)).thenReturn(false);
        when(privacySettingsService.getOrCreateEntity(hotelId)).thenReturn(customSettings);
        when(stayServiceClient.getLastStayDate(guestId))
                .thenReturn(new GuestLastStayClientResponse(true, lastStay));
        when(billingServiceClient.getLastInvoiceDate(guestId))
                .thenReturn(new GuestInvoiceClientResponse(false, null));

        final GdprLegalHoldException ex = assertThrows(
                GdprLegalHoldException.class, () -> service.deleteGuest(guestId));

        assertEquals(GdprLegalHoldException.LegalBasis.TULPS, ex.getLegalBasis());
        assertEquals(lastStay.plusYears(YEARS_7), ex.getUnlocksAt());
    }
}
