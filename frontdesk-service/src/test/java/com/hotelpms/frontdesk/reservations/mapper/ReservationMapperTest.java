package com.hotelpms.frontdesk.reservations.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.dto.ReservationResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * Pins the entity-to-response mapping the unmapped-target policy would otherwise
 * let silently drop (the mapper ignores unmapped targets).
 */
class ReservationMapperTest {

    private final ReservationMapper mapper = Mappers.getMapper(ReservationMapper.class);

    @Test
    void responseCarriesTheGroupId() {
        final UUID groupId = UUID.randomUUID();
        final Reservation entity = Reservation.builder().id(UUID.randomUUID()).groupId(groupId).build();

        final ReservationResponse response = mapper.toResponse(entity);

        assertEquals(groupId, response.groupId());
    }

    @Test
    void responseHasNoGroupIdForAStandaloneReservation() {
        final Reservation entity = Reservation.builder().id(UUID.randomUUID()).build();

        assertNull(mapper.toResponse(entity).groupId());
    }
}
