package com.hotelpms.gateway.events;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

/**
 * Browser-facing realtime event stream (T-GW-11). Reached via a
 * self-referencing {@code forward:} route in {@code api-gateway.yml} so the
 * request still passes through {@code AuthenticationFilter} and the four
 * global filters exactly like every proxied endpoint — {@code forward:}
 * dispatches in-process to this controller via {@code DispatcherHandler}, it
 * never goes through the reactive HTTP client, so streaming is unaffected by
 * any proxy-layer buffering.
 *
 * <p><b>This controller is deliberately NOT mapped at the public
 * {@code /api/v1/events/stream} path.</b> WebFlux's own
 * {@code RequestMappingHandlerMapping} (order 0) is queried by
 * {@code DispatcherHandler} before Gateway's {@code RoutePredicateHandlerMapping}
 * (order 1) — if this controller mapped the same path the route predicates
 * on, a direct request would resolve straight to this method and never touch
 * the route's filter chain at all, completely bypassing {@code AuthenticationFilter}
 * (a security-review finding during development, not a hypothetical: verified
 * by reasoning through Spring Cloud Gateway's handler-mapping order, and
 * guarded against by {@code EventStreamControllerMappingTest}). The route's
 * {@code uri: forward:} target is this controller's real, internal-only path;
 * the public path is only ever resolved by the Gateway route.
 *
 * <p>The hotel id is read from the {@code X-Auth-Hotel} header, which
 * {@code AuthenticationFilter} injects from the caller's JWT claim — never
 * from client-supplied input. A tenant can therefore only ever subscribe to
 * its own hotel's stream, closing the cross-tenant IDOR risk on this channel.
 */
@RestController
public class EventStreamController {

    /** Internal-only dispatch path — never call this directly; the public
     * entry point is the {@code events-stream} route in {@code api-gateway.yml},
     * which forwards here only after {@code AuthenticationFilter} has run. */
    public static final String INTERNAL_STREAM_PATH = "/local/events/stream";

    private static final String HEADER_HOTEL = "X-Auth-Hotel";

    /** Interval between keep-alive SSE comments, to stop reverse proxies and
     * browsers from timing out an idle connection (nginx's default
     * {@code proxy_read_timeout} is 60s). */
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final RoomEventBroadcaster broadcaster;

    public EventStreamController(final RoomEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /**
     * Streams realtime events for the caller's hotel.
     *
     * @param hotelHeader the {@code X-Auth-Hotel} header injected by
     *                    {@code AuthenticationFilter}
     * @return an infinite stream of {@link RoomEvent}s interleaved with
     *         keep-alive comments
     */
    @GetMapping(path = INTERNAL_STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RoomEvent>> stream(
            @RequestHeader(value = HEADER_HOTEL, required = false) final String hotelHeader) {
        final UUID hotelId = parseHotelId(hotelHeader);
        final Flux<ServerSentEvent<RoomEvent>> events = broadcaster.streamFor(hotelId)
                .map(event -> ServerSentEvent.builder(event).build());
        final Flux<ServerSentEvent<RoomEvent>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> ServerSentEvent.<RoomEvent>builder().comment("heartbeat").build());
        return Flux.merge(events, heartbeat);
    }

    private static UUID parseHotelId(final String hotelHeader) {
        if (hotelHeader == null || hotelHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid hotel context");
        }
        try {
            return UUID.fromString(hotelHeader);
        } catch (final IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid hotel context");
        }
    }
}
