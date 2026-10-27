package com.hotelpms.frontdesk.stays.mapper;

import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.domain.StayGuest;
import com.hotelpms.frontdesk.stays.domain.StayStatus;
import com.hotelpms.frontdesk.stays.domain.TravellerType;
import com.hotelpms.frontdesk.stays.dto.StayGuestRequest;
import com.hotelpms.frontdesk.stays.dto.StayGuestResponse;
import com.hotelpms.frontdesk.stays.dto.StayRequest;
import com.hotelpms.frontdesk.stays.dto.StayResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the primary-guest flag in responses: the entity's {@code boolean isPrimaryGuest}
 * is read by MapStruct as {@code primaryGuest}, so without an explicit mapping the record's
 * {@code isPrimaryGuest} stayed {@code false} for every guest while the database held the right value.
 */
class StayMapperPrimaryGuestTest {

    private static final String PRIMARY_NAME = "Marco";
    private static final String OTHER_NAME = "Anna";
    private static final String SURNAME = "Rossi";
    private static final String GENDER = "1";
    private static final String ISTAT_ITALY = "100000100";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1980, 1, 1);

    private final StayMapper stayMapper = Mappers.getMapper(StayMapper.class);
    private final StayGuestMapper stayGuestMapper = Mappers.getMapper(StayGuestMapper.class);

    private static StayGuest guest(final String firstName, final boolean primary) {
        return StayGuest.builder()
                .firstName(firstName)
                .lastName(SURNAME)
                .gender(GENDER)
                .dateOfBirth(BIRTH_DATE)
                .placeOfBirth(ISTAT_ITALY)
                .citizenship(ISTAT_ITALY)
                .isPrimaryGuest(primary)
                .travellerType(TravellerType.OSPITE_SINGOLO)
                .build();
    }

    @Test
    void stayGuestMapperKeepsThePrimaryFlag() {
        assertTrue(stayGuestMapper.toResponse(guest(PRIMARY_NAME, true)).isPrimaryGuest());
        assertFalse(stayGuestMapper.toResponse(guest(OTHER_NAME, false)).isPrimaryGuest());
    }

    @Test
    void stayMapperKeepsThePrimaryFlagOfNestedGuests() {
        final Stay stay = Stay.builder()
                .guests(List.of(guest(PRIMARY_NAME, true), guest(OTHER_NAME, false)))
                .build();

        final StayResponse response = stayMapper.toDto(stay);

        final List<StayGuestResponse> guests = response.guests();
        assertEquals(2, guests.size());
        assertTrue(guests.get(0).isPrimaryGuest());
        assertFalse(guests.get(1).isPrimaryGuest());
    }

    @Test
    void bothResponseMappersAgreeOnTheSameGuest() {
        final StayGuest primary = guest(PRIMARY_NAME, true);
        final Stay stay = Stay.builder().guests(List.of(primary)).build();

        assertEquals(stayGuestMapper.toResponse(primary), stayMapper.toDto(stay).guests().get(0));
    }

    @Test
    void checkInPayloadStillPersistsThePrimaryFlag() {
        // Routing the nested guests through StayGuestMapper.toEntity would drop it: that method
        // ignores the flag on purpose (addGuest must never create a second primary guest).
        final StayRequest request = new StayRequest(
                null, null, UUID.randomUUID(), UUID.randomUUID(), StayStatus.CHECKED_IN, null, null, null,
                List.of(new StayGuestRequest(
                        PRIMARY_NAME, SURNAME, GENDER, BIRTH_DATE, ISTAT_ITALY, ISTAT_ITALY,
                        null, null, null, true, TravellerType.OSPITE_SINGOLO, null, null)));

        final Stay stay = stayMapper.toEntity(request);

        assertTrue(stay.getGuests().get(0).isPrimaryGuest());
    }
}
