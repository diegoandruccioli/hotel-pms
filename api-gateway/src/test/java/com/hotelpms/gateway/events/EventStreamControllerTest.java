package com.hotelpms.gateway.events;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link EventStreamController}. The two error-path tests go through
 * {@link WebTestClient} to also verify the {@code @GetMapping}/HTTP-status
 * wiring; the two streaming-behavior tests call the controller method
 * directly with a real (non-mocked) {@link RoomEventBroadcaster} — WebTestClient's
 * mock connector doesn't reliably subscribe to an infinite {@code Flux}
 * body in time for a synchronous {@code broadcaster.publish()} to be seen,
 * so direct invocation is the more reliable option here, no Spring context
 * either way.
 */
class EventStreamControllerTest {

    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void returns401WhenHotelHeaderIsMissing() {
        final WebTestClient client = WebTestClient.bindToController(
                new EventStreamController(new RoomEventBroadcaster())).build();

        client.get().uri(EventStreamController.INTERNAL_STREAM_PATH)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void returns401WhenHotelHeaderIsNotAValidUuid() {
        final WebTestClient client = WebTestClient.bindToController(
                new EventStreamController(new RoomEventBroadcaster())).build();

        client.get().uri(EventStreamController.INTERNAL_STREAM_PATH)
                .header("X-Auth-Hotel", "not-a-uuid")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void streamsAnEventPublishedForTheCallersHotel() {
        final RoomEventBroadcaster broadcaster = new RoomEventBroadcaster();
        final EventStreamController controller = new EventStreamController(broadcaster);
        final RoomEvent event = RoomEvent.of(RoomEventType.CHECK_IN);

        StepVerifier.create(controller.stream(HOTEL_ID.toString()))
                .then(() -> broadcaster.publish(HOTEL_ID, event))
                .assertNext(sse -> assertThat(sse.data()).isEqualTo(event))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void doesNotStreamAnEventPublishedForADifferentHotel() {
        final RoomEventBroadcaster broadcaster = new RoomEventBroadcaster();
        final EventStreamController controller = new EventStreamController(broadcaster);
        final RoomEvent eventForOtherHotel = RoomEvent.of(RoomEventType.CHECK_OUT);
        final RoomEvent eventForThisHotel = RoomEvent.of(RoomEventType.ROOM_STATUS_CHANGED);

        StepVerifier.create(controller.stream(HOTEL_ID.toString()))
                .then(() -> {
                    broadcaster.publish(OTHER_HOTEL_ID, eventForOtherHotel);
                    broadcaster.publish(HOTEL_ID, eventForThisHotel);
                })
                .assertNext(sse -> assertThat(sse.data()).isEqualTo(eventForThisHotel))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }
}
