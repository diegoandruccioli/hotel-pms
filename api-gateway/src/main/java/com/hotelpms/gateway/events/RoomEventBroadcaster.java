package com.hotelpms.gateway.events;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one multicast {@link Sinks.Many} per hotel and fans out
 * {@link RoomEvent}s to every browser currently subscribed to that hotel's
 * SSE stream (T-GW-09). A hotel's sink is created lazily on first use and
 * kept for the lifetime of the process — the target scale is a single hotel
 * with at most ~20 concurrent users (README.md), so the map stays small and
 * eviction would be premature complexity.
 */
@Component
public class RoomEventBroadcaster {

    private final ConcurrentHashMap<UUID, Sinks.Many<RoomEvent>> sinks = new ConcurrentHashMap<>();

    /**
     * Publishes an event to every subscriber of the given hotel's stream.
     * A no-op (besides sink creation) if nobody is currently subscribed.
     *
     * @param hotelId the hotel the event belongs to
     * @param event   the event to publish
     */
    public void publish(final UUID hotelId, final RoomEvent event) {
        sinkFor(hotelId).tryEmitNext(event);
    }

    /**
     * Returns the event stream for a hotel, creating its sink if this is the
     * first subscriber. The hotel id here always comes from a server-verified
     * source (the caller's JWT claim, never client input) — a tenant can only
     * ever obtain a {@link Flux} for its own hotel.
     *
     * @param hotelId the hotel to stream events for
     * @return an infinite flux of events for that hotel
     */
    public Flux<RoomEvent> streamFor(final UUID hotelId) {
        return sinkFor(hotelId).asFlux();
    }

    private Sinks.Many<RoomEvent> sinkFor(final UUID hotelId) {
        return sinks.computeIfAbsent(hotelId, id -> Sinks.many().multicast().onBackpressureBuffer());
    }
}
