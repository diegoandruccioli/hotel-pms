package com.hotelpms.frontdesk.client;

import com.hotelpms.frontdesk.client.dto.GatewayEventNotifyRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OpenFeign client for api-gateway's internal realtime-event notify endpoint
 * (T-GW-09b). Signed automatically by the global {@code InternalFeignAuthInterceptor}
 * bean ({@code FeignHeaderConfig}) — no signing code needed here, same as
 * every other internal Feign client in this service.
 *
 * <p>Fire-and-forget by design: a missed realtime notification is not a
 * business failure (the browser can always reload), so the fallback just
 * logs and returns — it must never block or fail the room/stay mutation
 * that triggered the call.
 */
@FeignClient(name = "api-gateway")
public interface GatewayEventsClient {

    Logger LOG = LoggerFactory.getLogger(GatewayEventsClient.class);

    /** Resilience4j circuit breaker instance name for this client. */
    String CB_API_GATEWAY_EVENTS = "apiGatewayEvents";

    /**
     * Notifies api-gateway of a room/stay status change so it can push a
     * realtime event to subscribed browsers.
     *
     * @param request the event to publish
     */
    @PostMapping("/internal/events/notify")
    @CircuitBreaker(name = CB_API_GATEWAY_EVENTS, fallbackMethod = "notifyFallback")
    void notify(@RequestBody GatewayEventNotifyRequest request);

    /**
     * Fallback for {@link #notify} — circuit open or call failed.
     *
     * @param request   original request
     * @param throwable cause
     */
    default void notifyFallback(final GatewayEventNotifyRequest request, final Throwable throwable) {
        LOG.warn("api-gateway CB open — realtime event notification suppressed [type={}]: {}",
                request.type(), throwable.getMessage());
    }
}
