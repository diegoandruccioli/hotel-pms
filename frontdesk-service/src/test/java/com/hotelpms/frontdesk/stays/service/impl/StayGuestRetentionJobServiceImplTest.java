package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.StayInvoiceDateResponse;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.repository.StayGuestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StayGuestRetentionJobServiceImplTest {

    private static final int YEARS_3 = 3;
    private static final int YEARS_6 = 6;
    private static final int YEARS_11 = 11;
    private static final String FIRST_NAME_MARIO = "Mario";
    private static final int BIRTH_YEAR_1990 = 1990;

    @Mock private StayGuestRepository stayGuestRepository;
    @Mock private BillingClient billingClient;

    @InjectMocks
    private StayGuestRetentionJobServiceImpl job;

    private StayGuest buildStayGuest(final UUID hotelId, final LocalDate departureDate) {
        final Stay stay = Stay.builder().id(UUID.randomUUID()).hotelId(hotelId).build();
        return StayGuest.builder()
                .id(UUID.randomUUID())
                .stay(stay)
                .firstName(FIRST_NAME_MARIO)
                .lastName("Rossi")
                .gender("M")
                .dateOfBirth(LocalDate.of(BIRTH_YEAR_1990, 1, 1))
                .placeOfBirth("Bologna")
                .citizenship("IT")
                .documentType("CI")
                .documentNumber("AB123456")
                .isPrimaryGuest(true)
                .arrivalDate(departureDate.minusDays(2))
                .departureDate(departureDate)
                .active(true)
                .build();
    }

    @Test
    void shouldAnonymiseStayGuestWithBothHoldsExpired() {
        final UUID hotelId = UUID.randomUUID();
        final StayGuest guest = buildStayGuest(hotelId, LocalDate.now().minusYears(YEARS_11));
        final UUID stayId = Objects.requireNonNull(guest.getStay()).getId();

        when(stayGuestRepository.findByDepartureDateBefore(any(LocalDate.class)))
                .thenReturn(List.of(guest));
        when(billingClient.getLastInvoiceDateForStay(stayId))
                .thenReturn(new StayInvoiceDateResponse(true, LocalDate.now().minusYears(YEARS_11)));
        when(stayGuestRepository.save(Objects.requireNonNull(guest))).thenReturn(guest);

        job.runRetentionJob();

        assertFalse(guest.isActive());
        assertEquals("GDPR", guest.getFirstName());
        assertNull(guest.getDocumentNumber());
        verify(stayGuestRepository, times(1)).save(Objects.requireNonNull(guest));
    }

    @Test
    void shouldSkipStayGuestWithActiveTulpsHold() {
        final UUID hotelId = UUID.randomUUID();
        // Departed 11 years ago (past the FISCAL pre-filter) but arrivalDate/departureDate
        // set so TULPS (5y) has NOT expired: use a departure only 3 years back instead —
        // still passes the repository pre-filter mock (it's stubbed to return this guest
        // regardless of the real cutoff), exercising the in-job TULPS check directly.
        final StayGuest guest = buildStayGuest(hotelId, LocalDate.now().minusYears(YEARS_3));

        when(stayGuestRepository.findByDepartureDateBefore(any(LocalDate.class)))
                .thenReturn(List.of(guest));

        job.runRetentionJob();

        assertEquals(FIRST_NAME_MARIO, guest.getFirstName());
        assertTrue(guest.isActive());
        verify(stayGuestRepository, never()).save(any(StayGuest.class));
    }

    @Test
    void shouldSkipStayGuestWithActiveFiscalHold() {
        final UUID hotelId = UUID.randomUUID();
        final StayGuest guest = buildStayGuest(hotelId, LocalDate.now().minusYears(YEARS_11));
        final UUID stayId = Objects.requireNonNull(guest.getStay()).getId();

        when(stayGuestRepository.findByDepartureDateBefore(any(LocalDate.class)))
                .thenReturn(List.of(guest));
        when(billingClient.getLastInvoiceDateForStay(stayId))
                .thenReturn(new StayInvoiceDateResponse(true, LocalDate.now().minusYears(YEARS_6)));

        job.runRetentionJob();

        assertEquals(FIRST_NAME_MARIO, guest.getFirstName());
        assertTrue(guest.isActive());
    }

    @Test
    void shouldSkipStayGuestWhenBillingServiceIndeterminate() {
        final UUID hotelId = UUID.randomUUID();
        final StayGuest guest = buildStayGuest(hotelId, LocalDate.now().minusYears(YEARS_11));
        final UUID stayId = Objects.requireNonNull(guest.getStay()).getId();

        when(stayGuestRepository.findByDepartureDateBefore(any(LocalDate.class)))
                .thenReturn(List.of(guest));
        // Circuit-breaker fallback shape: hasInvoices=true, date unknown — must block.
        when(billingClient.getLastInvoiceDateForStay(stayId))
                .thenReturn(new StayInvoiceDateResponse(true, null));

        job.runRetentionJob();

        assertTrue(guest.isActive());
        verify(stayGuestRepository, never()).save(any(StayGuest.class));
    }

    @Test
    void shouldSkipStayGuestStillInHouse() {
        final UUID hotelId = UUID.randomUUID();
        final StayGuest guest = buildStayGuest(hotelId, LocalDate.now().minusYears(YEARS_11));
        guest.setDepartureDate(null);

        when(stayGuestRepository.findByDepartureDateBefore(any(LocalDate.class)))
                .thenReturn(List.of(guest));

        job.runRetentionJob();

        assertTrue(guest.isActive());
        verify(stayGuestRepository, never()).save(any(StayGuest.class));
    }
}
