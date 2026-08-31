package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.citytax.service.CityTaxAssessmentService;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.domain.TravellerType;
import com.hotelpms.frontdesk.stays.dto.StayGuestRequest;
import com.hotelpms.frontdesk.stays.dto.StayGuestResponse;
import com.hotelpms.frontdesk.stays.mapper.StayGuestMapper;
import com.hotelpms.frontdesk.stays.mapper.StayGuestMapperImpl;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StayGuestServiceImplTest {

    private static final String COMUNE_CODICE = "058091000";
    private static final String CITIZENSHIP_ITALIA = "100000100";
    private static final String GUEST_FIRST_NAME = "Mario";
    private static final LocalDate GUEST_DOB = LocalDate.of(1990, 1, 1);
    private static final LocalDate EXISTING_GUEST_DOB = LocalDate.of(1992, 3, 3);

    @Mock
    private StayRepository stayRepository;

    @Mock
    private CityTaxAssessmentService cityTaxAssessmentService;

    private final StayGuestMapper stayGuestMapper = new StayGuestMapperImpl();

    private StayGuestServiceImpl stayGuestService;

    private UUID stayId;
    private UUID hotelId;
    private Stay stay;

    @BeforeEach
    void setUp() {
        stayId = UUID.randomUUID();
        hotelId = UUID.randomUUID();
        stay = Stay.builder()
                .id(stayId)
                .hotelId(hotelId)
                .status(StayStatus.CHECKED_IN)
                .guests(new ArrayList<>())
                .build();

        final StayGuestValidator validator = new StayGuestValidator(stayRepository);
        stayGuestService = new StayGuestServiceImpl(
                stayRepository, validator, stayGuestMapper, cityTaxAssessmentService);
    }

    private StayGuestRequest request(final boolean primary) {
        return new StayGuestRequest(GUEST_FIRST_NAME, "Rossi", "M", GUEST_DOB,
                COMUNE_CODICE, CITIZENSHIP_ITALIA, "PASOR", "AA1234567", COMUNE_CODICE,
                primary, TravellerType.OSPITE_SINGOLO, null, null);
    }

    private StayGuest existingGuest(final boolean primary, final boolean sent) {
        final StayGuest guest = StayGuest.builder()
                .id(UUID.randomUUID())
                .stay(stay)
                .firstName("Anna")
                .lastName("Verdi")
                .gender("F")
                .dateOfBirth(EXISTING_GUEST_DOB)
                .placeOfBirth(COMUNE_CODICE)
                .citizenship(CITIZENSHIP_ITALIA)
                .isPrimaryGuest(primary)
                .arrivalDate(LocalDate.now())
                .alloggiatiSent(sent)
                .travellerType(TravellerType.OSPITE_SINGOLO)
                .build();
        stay.getGuests().add(guest);
        return guest;
    }

    @Test
    void addGuestStampsArrivalDateAndTriggersRectification() {
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(stayRepository.save(stay)).thenReturn(stay);

        final StayGuestResponse response = stayGuestService.addGuest(stayId, hotelId, request(false));

        assertEquals(GUEST_FIRST_NAME, response.firstName());
        assertEquals(1, stay.getGuests().size());
        assertEquals(LocalDate.now(), stay.getGuests().get(0).getArrivalDate());
        verify(cityTaxAssessmentService).rectifyForGuestAdded(eq(stay), any(StayGuest.class));
    }

    @Test
    void addGuestOnStayNotCheckedInThrows() {
        stay.setStatus(StayStatus.CHECKED_OUT);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(IllegalStateException.class, () -> stayGuestService.addGuest(stayId, hotelId, request(false)));
        verify(cityTaxAssessmentService, never()).rectifyForGuestAdded(any(), any());
    }

    @Test
    void addGuestOnUnknownStayThrowsNotFound() {
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> stayGuestService.addGuest(stayId, hotelId, request(false)));
    }

    @Test
    void updateGuestFlagsNeedsResubmitWhenAlreadySent() {
        final StayGuest guest = existingGuest(false, true);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(stayRepository.save(stay)).thenReturn(stay);

        final StayGuestResponse response = stayGuestService.updateGuest(stayId, guest.getId(), hotelId, request(false));

        assertEquals(GUEST_FIRST_NAME, response.firstName());
        assertTrue(guest.isNeedsResubmit());
    }

    @Test
    void updateGuestDoesNotFlagResubmitWhenNeverSent() {
        final StayGuest guest = existingGuest(false, false);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(stayRepository.save(stay)).thenReturn(stay);

        stayGuestService.updateGuest(stayId, guest.getId(), hotelId, request(false));

        assertFalse(guest.isNeedsResubmit());
    }

    @Test
    void updateGuestForUnknownGuestIdThrowsNotFound() {
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(NotFoundException.class,
                () -> stayGuestService.updateGuest(stayId, UUID.randomUUID(), hotelId, request(false)));
    }

    @Test
    void removeGuestDeletesWhenNeverSentAndNotPrimary() {
        final StayGuest guest = existingGuest(false, false);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(stayRepository.save(stay)).thenReturn(stay);

        stayGuestService.removeGuest(stayId, guest.getId(), hotelId);

        assertTrue(stay.getGuests().isEmpty());
    }

    @Test
    void removeGuestAlreadySentThrowsConflict() {
        final StayGuest guest = existingGuest(false, true);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(ConflictException.class, () -> stayGuestService.removeGuest(stayId, guest.getId(), hotelId));
        verify(stayRepository, never()).save(any());
    }

    @Test
    void removePrimaryGuestThrowsConflict() {
        final StayGuest guest = existingGuest(true, false);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(ConflictException.class, () -> stayGuestService.removeGuest(stayId, guest.getId(), hotelId));
    }

    @Test
    void recordDepartureSetsDepartureDate() {
        final StayGuest guest = existingGuest(false, false);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(stayRepository.save(stay)).thenReturn(stay);
        final LocalDate departure = LocalDate.now().plusDays(1);

        final StayGuestResponse response = stayGuestService.recordDeparture(stayId, guest.getId(), hotelId, departure);

        assertEquals(guest.getId(), response.id());
        assertEquals(departure, guest.getDepartureDate());
    }

    @Test
    void recordDepartureBeforeArrivalThrowsBadRequest() {
        final StayGuest guest = existingGuest(false, false);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));

        assertThrows(BadRequestException.class,
                () -> stayGuestService.recordDeparture(stayId, guest.getId(), hotelId, guest.getArrivalDate().minusDays(1)));
    }

    @Test
    void promotePrimaryDemotesEveryOtherGuest() {
        final StayGuest oldPrimary = existingGuest(true, false);
        final StayGuest newPrimary = existingGuest(false, false);
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        when(stayRepository.save(stay)).thenReturn(stay);

        stayGuestService.promotePrimary(stayId, newPrimary.getId(), hotelId);

        assertTrue(newPrimary.isPrimaryGuest());
        assertFalse(oldPrimary.isPrimaryGuest());
    }
}
