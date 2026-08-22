package com.hotelpms.gateway.events;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link RoomEventBroadcaster}. No Spring context.
 */
class RoomEventBroadcasterTest {

    private static final UUID HOTEL_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HOTEL_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void deliversAPublishedEventToASubscriberOfTheSameHotel() {
        final RoomEventBroadcaster broadcaster = new RoomEventBroadcaster();
        final RoomEvent event = RoomEvent.of(RoomEventType.ROOM_STATUS_CHANGED);

        StepVerifier.create(broadcaster.streamFor(HOTEL_A).take(1))
                .then(() -> broadcaster.publish(HOTEL_A, event))
                .expectNext(event)
                .verifyComplete();
    }

    @Test
    void doesNotDeliverAnEventPublishedToADifferentHotel() {
        final RoomEventBroadcaster broadcaster = new RoomEventBroadcaster();
        final RoomEvent eventForOtherHotel = RoomEvent.of(RoomEventType.CHECK_IN);
        final RoomEvent eventForThisHotel = RoomEvent.of(RoomEventType.CHECK_OUT);

        StepVerifier.create(broadcaster.streamFor(HOTEL_A).take(1))
                .then(() -> {
                    broadcaster.publish(HOTEL_B, eventForOtherHotel);
                    broadcaster.publish(HOTEL_A, eventForThisHotel);
                })
                .expectNext(eventForThisHotel)
                .verifyComplete();
    }

    @Test
    void fansOutOneEventToMultipleSubscribersOfTheSameHotel() {
        final RoomEventBroadcaster broadcaster = new RoomEventBroadcaster();
        final RoomEvent event = RoomEvent.of(RoomEventType.CHECK_OUT);

        final List<RoomEvent> receivedByFirst = new ArrayList<>();
        final List<RoomEvent> receivedBySecond = new ArrayList<>();
        broadcaster.streamFor(HOTEL_A).take(1).subscribe(receivedByFirst::add);
        broadcaster.streamFor(HOTEL_A).take(1).subscribe(receivedBySecond::add);

        broadcaster.publish(HOTEL_A, event);

        assertThat(receivedByFirst).containsExactly(event);
        assertThat(receivedBySecond).containsExactly(event);
    }

    @Test
    void publishingWithNoSubscriberDoesNotThrow() {
        final RoomEventBroadcaster broadcaster = new RoomEventBroadcaster();

        broadcaster.publish(HOTEL_A, RoomEvent.of(RoomEventType.ROOM_STATUS_CHANGED));

        assertThat(broadcaster.streamFor(HOTEL_A)).isNotNull();
    }
}
