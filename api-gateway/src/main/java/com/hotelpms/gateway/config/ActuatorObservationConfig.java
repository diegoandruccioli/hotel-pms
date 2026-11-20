package com.hotelpms.gateway.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * Keeps the gateway's own actuator requests out of tracing and HTTP metrics.
 *
 * <p>Docker's healthcheck and Prometheus each call {@code /actuator/*} every 15 s, around the clock. With
 * sampling at 1.0 each call became a span sent to Zipkin: a large share of the ~240 spans/min measured on an idle
 * stack, which filled Zipkin's in-memory storage with nothing a person would ever look at. Business requests
 * ({@code /api/**}) are untouched.
 */
@Configuration
public class ActuatorObservationConfig {

    private static final String ACTUATOR_PATH = "/actuator";
    private static final String ACTUATOR_PATH_PREFIX = ACTUATOR_PATH + "/";

    /**
     * Rejects observations of HTTP requests addressed to the actuator.
     *
     * @return a predicate that is {@code false} (observation skipped) only for actuator requests
     */
    @Bean
    public static ObservationPredicate actuatorObservationPredicate() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request
                && isActuator(request.getCarrier().getPath().value()));
    }

    private static boolean isActuator(final String path) {
        return ACTUATOR_PATH.equals(path) || path.startsWith(ACTUATOR_PATH_PREFIX);
    }
}
