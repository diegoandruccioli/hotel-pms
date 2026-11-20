package com.hotelpms.gateway.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalErrorWebExceptionHandler}.
 *
 * <p>The gateway answers its own 4xx (unknown path, wrong method, malformed input) with that
 * status, and keeps 502/504 for genuine downstream failures. No Spring context is loaded.
 */
class GlobalErrorWebExceptionHandlerTest {

    private static final String PATH = "/actuator/health";
    private static final String INTERNAL_HOST = "fb-service";
    private static final String FIELD_DETAIL = "detail";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_STATUS = "status";
    private static final String DETAIL_EXTERNAL = "EXTERNAL_SERVICE_ERROR";
    private static final String DETAIL_TIMEOUT = "GATEWAY_TIMEOUT";
    private static final String ERRORS_BASE = "https://hotel-pms.com/errors/";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private GlobalErrorWebExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalErrorWebExceptionHandler(mapper);
    }

    private MockServerWebExchange exchange(final HttpMethod method) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, PATH).build());
    }

    private static NoResourceFoundException noResource() {
        return new NoResourceFoundException(URI.create(PATH), "actuator/health");
    }

    private MockServerHttpResponse run(final MockServerWebExchange exchange, final Throwable ex) {
        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();
        return exchange.getResponse();
    }

    private JsonNode body(final MockServerHttpResponse response) {
        return mapper.readTree(response.getBodyAsString().block());
    }

    @Nested
    @DisplayName("gateway's own 4xx")
    class ClientErrors {

        @Test
        @DisplayName("unknown path (NoResourceFoundException) answers 404 NOT_FOUND, not 502")
        void unknownPathIs404() {
            final MockServerHttpResponse response = run(exchange(HttpMethod.GET),
                    noResource());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            final JsonNode json = body(response);
            assertThat(json.get(FIELD_STATUS).asInt()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(json.get(FIELD_DETAIL).asString()).isEqualTo("NOT_FOUND");
            assertThat(json.get(FIELD_TYPE).asString()).isEqualTo(ERRORS_BASE + "not-found");
            assertThat(json.get(FIELD_TITLE).asString()).isEqualTo("Not Found");
            assertThat(json.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("the body never carries the exception message (finding #17)")
        void bodyHasNoExceptionMessage() {
            final MockServerHttpResponse response = run(exchange(HttpMethod.GET),
                    noResource());

            assertThat(response.getBodyAsString().block()).doesNotContain("static resource");
        }

        @Test
        @DisplayName("wrong method (MethodNotAllowedException) answers 405 with the Allow header")
        void wrongMethodIs405() {
            final MockServerHttpResponse response = run(exchange(HttpMethod.DELETE),
                    new MethodNotAllowedException(HttpMethod.DELETE, List.of(HttpMethod.GET)));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(body(response).get(FIELD_DETAIL).asString()).isEqualTo("METHOD_NOT_ALLOWED");
            assertThat(body(response).get(FIELD_TYPE).asString()).isEqualTo(ERRORS_BASE + "method-not-allowed");
            assertThat(response.getHeaders().getFirst(HttpHeaders.ALLOW)).isEqualTo("GET");
        }

        @Test
        @DisplayName("malformed input (ServerWebInputException) answers 400 BAD_REQUEST")
        void malformedInputIs400() {
            final MockServerHttpResponse response = run(exchange(HttpMethod.GET),
                    new ServerWebInputException("bad"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(body(response).get(FIELD_DETAIL).asString()).isEqualTo("BAD_REQUEST");
        }
    }

    @Nested
    @DisplayName("genuine downstream failures")
    class DownstreamFailures {

        @Test
        @DisplayName("wrapped ConnectException answers 502 EXTERNAL_SERVICE_ERROR without the host name")
        void connectExceptionIs502() {
            final Throwable ex = new IllegalStateException("wrapper",
                    new ConnectException("Connection refused: " + INTERNAL_HOST));

            final MockServerHttpResponse response = run(exchange(HttpMethod.GET), ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(body(response).get(FIELD_DETAIL).asString()).isEqualTo(DETAIL_EXTERNAL);
            assertThat(body(response).get(FIELD_TYPE).asString()).isEqualTo(ERRORS_BASE + "external-service-error");
            assertThat(response.getBodyAsString().block()).doesNotContain(INTERNAL_HOST);
        }

        @Test
        @DisplayName("UnknownHostException answers 502")
        void unknownHostIs502() {
            final MockServerHttpResponse response = run(exchange(HttpMethod.GET),
                    new UnknownHostException(INTERNAL_HOST));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(body(response).get(FIELD_DETAIL).asString()).isEqualTo(DETAIL_EXTERNAL);
        }

        @Test
        @DisplayName("java.util.concurrent.TimeoutException answers 504 GATEWAY_TIMEOUT")
        void timeoutIs504() {
            final MockServerHttpResponse response = run(exchange(HttpMethod.GET),
                    new java.util.concurrent.TimeoutException("slow"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
            assertThat(body(response).get(FIELD_DETAIL).asString()).isEqualTo(DETAIL_TIMEOUT);
            assertThat(body(response).get(FIELD_TYPE).asString()).isEqualTo(ERRORS_BASE + "gateway-timeout");
        }

        @Test
        @DisplayName("route timeout (ResponseStatusException 504 caused by gateway TimeoutException) answers 504")
        void gatewayRouteTimeoutIs504() {
            final Throwable ex = new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "slow",
                    new TimeoutException("Response took longer than timeout"));

            final MockServerHttpResponse response = run(exchange(HttpMethod.GET), ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
            assertThat(body(response).get(FIELD_DETAIL).asString()).isEqualTo(DETAIL_TIMEOUT);
        }

        @Test
        @DisplayName("an unrecognised exception keeps falling back to 502")
        void unknownExceptionIs502() {
            final MockServerHttpResponse response = run(exchange(HttpMethod.GET),
                    new IllegalStateException("boom"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(body(response).get(FIELD_DETAIL).asString()).isEqualTo(DETAIL_EXTERNAL);
        }

        @Test
        @DisplayName("a 5xx ResponseStatusException other than 504 stays 502")
        void other5xxStaysBadGateway() {
            final MockServerHttpResponse response = run(exchange(HttpMethod.GET),
                    new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }
    }
}
