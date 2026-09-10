package com.hotelpms.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CorrelationIdFilter}.
 *
 * <p>No Spring context is loaded. The filter is exercised through a
 * {@link MockServerWebExchange} with a chain lambda that captures the mutated
 * exchange seen downstream, allowing assertions on the header the next filter
 * (or the routed service) would actually receive.
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    private ServerWebExchange run(@NonNull final MockServerHttpRequest request) {
        final MockServerWebExchange exchange = MockServerWebExchange.from(request);
        final AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        StepVerifier.create(
                filter.filter(exchange, ex -> {
                    downstream.set(ex);
                    ex.getResponse().setStatusCode(HttpStatus.OK);
                    return ex.getResponse().setComplete();
                })
        ).verifyComplete();
        return downstream.get();
    }

    @Nested
    @DisplayName("CWE-117: X-Correlation-ID validation before it reaches downstream MDC/logs")
    class ValidationTests {

        @Test
        @DisplayName("preserves a well-formed client-supplied correlation ID")
        void preservesValidCorrelationId() {
            final ServerWebExchange downstream = run(
                    MockServerHttpRequest.get("/api/v1/frontdesk/rooms")
                            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "req-abc123_XYZ-9")
                            .build());

            assertThat(downstream.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                    .isEqualTo("req-abc123_XYZ-9");
        }

        @Test
        @DisplayName("replaces a correlation ID containing CRLF with a generated UUID")
        void replacesCorrelationIdContainingCrlf() {
            final ServerWebExchange downstream = run(
                    MockServerHttpRequest.get("/api/v1/frontdesk/rooms")
                            .header(CorrelationIdFilter.CORRELATION_ID_HEADER,
                                    "abc\r\n[AUTH] LOGIN_SUCCESS | user=admin")
                            .build());

            final String forwarded = downstream.getRequest().getHeaders()
                    .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertThat(forwarded).doesNotContain("\r", "\n");
            assertThat(UUID.fromString(forwarded)).isNotNull();
        }

        @Test
        @DisplayName("replaces a correlation ID longer than 64 characters with a generated UUID")
        void replacesCorrelationIdExceedingMaxLength() {
            final String tooLong = "a".repeat(65);

            final ServerWebExchange downstream = run(
                    MockServerHttpRequest.get("/api/v1/frontdesk/rooms")
                            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, tooLong)
                            .build());

            final String forwarded = downstream.getRequest().getHeaders()
                    .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertThat(forwarded).isNotEqualTo(tooLong);
            assertThat(UUID.fromString(forwarded)).isNotNull();
        }

        @Test
        @DisplayName("generates a UUID when the header is absent")
        void generatesUuidWhenHeaderAbsent() {
            final ServerWebExchange downstream = run(
                    MockServerHttpRequest.get("/api/v1/frontdesk/rooms").build());

            final String forwarded = downstream.getRequest().getHeaders()
                    .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertThat(UUID.fromString(forwarded)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Filter ordering")
    class FilterOrderTests {

        @Test
        @DisplayName("getOrder returns HIGHEST_PRECEDENCE")
        void shouldHaveCorrectOrder() {
            assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        }
    }
}
