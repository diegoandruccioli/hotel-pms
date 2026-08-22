package com.hotelpms.gateway.events;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit tests for {@link InternalEventsController} — no Spring
 * context, no {@link InternalEventsAuthFilter} in the loop, so the verified
 * hotel-id exchange attribute is set by hand, exactly as the filter would.
 */
class InternalEventsControllerTest {

    private static final String HOTEL_ID_ATTRIBUTE = InternalEventsAuthFilter.class.getName() + ".hotelId";
    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void publishesTheRequestedEventToTheVerifiedHotel() {
        final RoomEventBroadcaster broadcaster = new RoomEventBroadcaster();
        final InternalEventsController controller = new InternalEventsController(broadcaster);
        final MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.post("/internal/events/notify").build());
        exchange.getAttributes().put(HOTEL_ID_ATTRIBUTE, HOTEL_ID.toString());

        final List<RoomEvent> received = new java.util.ArrayList<>();
        broadcaster.streamFor(HOTEL_ID).take(1).subscribe(received::add);

        StepVerifier.create(controller.notify(
                        new NotifyEventRequest(RoomEventType.CHECK_IN), exchange))
                .verifyComplete();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).type()).isEqualTo(RoomEventType.CHECK_IN);
    }

    @Test
    void rejectsWhenNoVerifiedHotelAttributeIsPresent() {
        final InternalEventsController controller = new InternalEventsController(new RoomEventBroadcaster());
        final MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.post("/internal/events/notify").build());

        StepVerifier.create(controller.notify(new NotifyEventRequest(RoomEventType.CHECK_OUT), exchange))
                .expectErrorMatches(error -> error instanceof ResponseStatusException
                        && ((ResponseStatusException) error).getStatusCode().value() == 401)
                .verify();
    }
}
