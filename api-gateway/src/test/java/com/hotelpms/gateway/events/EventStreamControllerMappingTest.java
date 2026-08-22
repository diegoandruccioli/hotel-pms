package com.hotelpms.gateway.events;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for a real vulnerability caught in security review before
 * this feature shipped: {@link EventStreamController} used to be mapped at
 * the SAME path (`/api/v1/events/stream`) its `events-stream` Gateway route
 * predicates on. WebFlux's own {@code RequestMappingHandlerMapping} (order 0)
 * is consulted by {@code DispatcherHandler} before Gateway's
 * {@code RoutePredicateHandlerMapping} (order 1), so a direct request to that
 * path resolved straight to the controller and skipped the route's filter
 * chain entirely — {@code AuthenticationFilter} never ran, and an
 * unauthenticated caller could supply an arbitrary {@code X-Auth-Hotel}
 * header and read another tenant's realtime stream.
 *
 * <p>The fix is structural: the controller must be mapped at a path
 * (currently {@link EventStreamController#INTERNAL_STREAM_PATH}) distinct
 * from the public path the {@code events-stream} route predicates on
 * (hardcoded below, mirroring the {@code Path=} predicate in
 * {@code config-service/src/main/resources/config/api-gateway.yml}). This
 * test cannot see that YAML file from this module, so it can't verify the
 * route's forward target stays wired to the controller's real path — but it
 * does verify the one invariant a regression would violate: the two paths
 * must never be equal.
 */
class EventStreamControllerMappingTest {

    /** Mirrors the `Path=` predicate of the `events-stream` route in
     * config-service/src/main/resources/config/api-gateway.yml — the public,
     * browser-facing URL. Kept here as a literal (not a shared constant)
     * deliberately: this test exists specifically to catch someone making the
     * two paths equal again, so it must not derive its expectation from the
     * same source the regression would change. */
    private static final String PUBLIC_ROUTE_PATH = "/api/v1/events/stream";

    @Test
    void controllerIsNotMappedAtThePubliclyRoutedPath() throws NoSuchMethodException {
        final Method streamMethod = EventStreamController.class.getMethod("stream", String.class);
        final GetMapping mapping = streamMethod.getAnnotation(GetMapping.class);

        assertThat(mapping.path()).containsExactly(EventStreamController.INTERNAL_STREAM_PATH);
        assertThat(EventStreamController.INTERNAL_STREAM_PATH).isNotEqualTo(PUBLIC_ROUTE_PATH);
    }
}
