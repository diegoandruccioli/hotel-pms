package com.hotelpms.gateway.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorObservationConfigTest {

    private static final String OBSERVATION_NAME = "http.server.requests";

    private final ObservationPredicate predicate = ActuatorObservationConfig.actuatorObservationPredicate();

    private static ServerRequestObservationContext requestTo(final String path) {
        return new ServerRequestObservationContext(
                MockServerHttpRequest.get(path).build(), new MockServerHttpResponse(), Map.of());
    }

    @Test
    @DisplayName("actuator health and prometheus requests are not observed")
    void shouldSkipActuatorEndpoints() {
        assertThat(predicate.test(OBSERVATION_NAME, requestTo("/actuator/health"))).isFalse();
        assertThat(predicate.test(OBSERVATION_NAME, requestTo("/actuator/health/readiness"))).isFalse();
        assertThat(predicate.test(OBSERVATION_NAME, requestTo("/actuator/prometheus"))).isFalse();
    }

    @Test
    @DisplayName("the bare /actuator path is not observed either")
    void shouldSkipActuatorRoot() {
        assertThat(predicate.test(OBSERVATION_NAME, requestTo("/actuator"))).isFalse();
    }

    @Test
    @DisplayName("business requests are still observed")
    void shouldKeepBusinessRequests() {
        assertThat(predicate.test(OBSERVATION_NAME, requestTo("/api/v1/invoices"))).isTrue();
        assertThat(predicate.test(OBSERVATION_NAME, requestTo("/api/v1/auth/login"))).isTrue();
    }

    @Test
    @DisplayName("a path that only starts with the word actuator is still observed")
    void shouldKeepLookalikePaths() {
        assertThat(predicate.test(OBSERVATION_NAME, requestTo("/actuators"))).isTrue();
        assertThat(predicate.test(OBSERVATION_NAME, requestTo("/api/actuator"))).isTrue();
    }

    @Test
    @DisplayName("observations that are not HTTP server requests are untouched")
    void shouldKeepNonHttpObservations() {
        assertThat(predicate.test("lettuce", new Observation.Context())).isTrue();
    }
}
