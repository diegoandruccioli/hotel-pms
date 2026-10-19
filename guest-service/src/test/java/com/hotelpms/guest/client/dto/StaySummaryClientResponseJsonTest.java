package com.hotelpms.guest.client.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The frontdesk stay-history payload gained a {@code reservationId} (so the guest sheet can link to
 * the reservation). This Feign mirror only feeds the GDPR Art. 20 export and deliberately does not
 * declare it, so the app's own Jackson configuration must tolerate the extra property — otherwise the
 * export's stay call would fail and fall back to an empty list — and must not write it back out.
 */
@JsonTest
class StaySummaryClientResponseJsonTest {

    private static final String PAYLOAD_WITH_RESERVATION =
            "{\"stayId\":\"%s\",\"checkInTime\":\"2026-10-01T14:00:00\","
                    + "\"checkOutTime\":\"2026-10-03T10:00:00\",\"roomId\":\"%s\","
                    + "\"status\":\"CHECKED_OUT\",\"reservationId\":\"%s\"}";

    /** The application class enables JPA auditing, which a JSON slice has no metamodel for. */
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void ignoresTheReservationIdTheStayServiceNowSends() {
        final UUID stayId = UUID.randomUUID();
        final UUID roomId = UUID.randomUUID();
        final String json = PAYLOAD_WITH_RESERVATION.formatted(stayId, roomId, UUID.randomUUID());

        final StaySummaryClientResponse stay = jsonMapper.readValue(json, StaySummaryClientResponse.class);

        assertEquals(stayId, stay.stayId());
        assertEquals(roomId, stay.roomId());
        assertEquals("CHECKED_OUT", stay.status());
    }

    @Test
    void theExportedStayDoesNotCarryTheReservationId() {
        final String json = PAYLOAD_WITH_RESERVATION.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        final StaySummaryClientResponse stay = jsonMapper.readValue(json, StaySummaryClientResponse.class);

        final String exported = jsonMapper.writeValueAsString(stay);

        assertFalse(exported.contains("reservationId"));
    }
}
