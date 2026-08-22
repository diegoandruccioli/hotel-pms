package com.hotelpms.gateway.events;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Receives internal, HMAC-signed notifications from frontdesk-service and
 * fans them out to browser clients via {@link RoomEventBroadcaster}
 * (T-GW-09b). Guarded by {@link InternalEventsAuthFilter}, which runs as a
 * plain {@code WebFilter} — not tied to gateway route matching, so this
 * endpoint needs no entry in {@code api-gateway.yml} and is unreachable
 * without a valid signature regardless of routing.
 */
@RestController
public class InternalEventsController {

    private static final String HOTEL_ID_ATTRIBUTE = InternalEventsAuthFilter.class.getName() + ".hotelId";

    private final RoomEventBroadcaster broadcaster;

    public InternalEventsController(final RoomEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /**
     * Publishes an event to the hotel that {@link InternalEventsAuthFilter}
     * verified the caller belongs to.
     *
     * @param request the event to publish
     * @param exchange the current exchange, carrying the verified hotel id
     * @return an empty completion signal
     */
    @PostMapping("/internal/events/notify")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> notify(@RequestBody final NotifyEventRequest request, final ServerWebExchange exchange) {
        final String hotelIdAttribute = exchange.getAttribute(HOTEL_ID_ATTRIBUTE);
        if (hotelIdAttribute == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing verified hotel context"));
        }
        final UUID hotelId = UUID.fromString(hotelIdAttribute);
        broadcaster.publish(hotelId, RoomEvent.of(request.type()));
        return Mono.empty();
    }
}
