package com.hotelpms.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CsrfFilter}.
 *
 * <p>No Spring context is loaded. The filter is exercised through a
 * {@link MockServerWebExchange} with a chain lambda that marks the response OK,
 * allowing assertions on the final status code to distinguish pass-through from
 * rejection.
 */
class CsrfFilterTest {

    private static final String VALID_TOKEN = "550e8400-e29b-41d4-a716-446655440000";

    private CsrfFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CsrfFilter();
    }

    /**
     * Runs the filter with a chain that returns 200, then returns the exchange for
     * assertions.
     */
    private MockServerWebExchange run(@NonNull final MockServerHttpRequest request) {
        final MockServerWebExchange exchange = MockServerWebExchange.from(request);
        StepVerifier.create(
                filter.filter(exchange, ex -> {
                    ex.getResponse().setStatusCode(HttpStatus.OK);
                    return ex.getResponse().setComplete();
                })
        ).verifyComplete();
        return exchange;
    }

    // ── Safe methods ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Safe methods — no CSRF check, pass through")
    class SafeMethodTests {

        @Test
        @DisplayName("GET passes through without cookie or header")
        void getPassesThrough() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.get("/api/v1/guests").build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("HEAD passes through without cookie or header")
        void headPassesThrough() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.method(Objects.requireNonNull(HttpMethod.HEAD), "/api/v1/guests").build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("OPTIONS passes through without cookie or header")
        void optionsPassesThrough() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.options("/api/v1/guests").build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ── Excluded paths ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Excluded pre-auth paths — pass through without CSRF token")
    class ExcludedPathTests {

        @Test
        @DisplayName("POST /api/v1/auth/login is excluded")
        void loginExcluded() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.post("/api/v1/auth/login").build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /api/v1/auth/register is excluded")
        void registerExcluded() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.post("/api/v1/auth/register").build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("POST /api/v1/auth/refresh is excluded")
        void refreshExcluded() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.post("/api/v1/auth/refresh").build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ── Missing or invalid token ──────────────────────────────────────────────

    @Nested
    @DisplayName("Missing or mismatched CSRF token — 403 Forbidden")
    class InvalidCsrfTests {

        @Test
        @DisplayName("POST without cookie and without header returns 403")
        void missingBothReturns403() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.post("/api/v1/guests").build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("POST with header but without cookie returns 403")
        void missingCookieReturns403() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.post("/api/v1/guests")
                            .header(CsrfFilter.CSRF_HEADER_NAME, VALID_TOKEN)
                            .build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("POST with cookie but without header returns 403")
        void missingHeaderReturns403() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.post("/api/v1/guests")
                            .cookie(new HttpCookie(CsrfFilter.CSRF_COOKIE_NAME, VALID_TOKEN))
                            .build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("POST with mismatched cookie and header values returns 403")
        void mismatchedTokenReturns403() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.post("/api/v1/guests")
                            .cookie(new HttpCookie(CsrfFilter.CSRF_COOKIE_NAME, VALID_TOKEN))
                            .header(CsrfFilter.CSRF_HEADER_NAME, "wrong-token")
                            .build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("DELETE with blank cookie value returns 403")
        void blankCookieReturns403() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.delete("/api/v1/guests/abc")
                            .cookie(new HttpCookie(CsrfFilter.CSRF_COOKIE_NAME, "   "))
                            .header(CsrfFilter.CSRF_HEADER_NAME, VALID_TOKEN)
                            .build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("PATCH with blank header value returns 403")
        void blankHeaderReturns403() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.method(Objects.requireNonNull(HttpMethod.PATCH), "/api/v1/guests/abc")
                            .cookie(new HttpCookie(CsrfFilter.CSRF_COOKIE_NAME, VALID_TOKEN))
                            .header(CsrfFilter.CSRF_HEADER_NAME, "  ")
                            .build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── Valid token ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid matching CSRF token — pass through")
    class ValidCsrfTests {

        @Test
        @DisplayName("POST with matching cookie and header returns 200")
        void validPostPassesThrough() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.post("/api/v1/reservations")
                            .cookie(new HttpCookie(CsrfFilter.CSRF_COOKIE_NAME, VALID_TOKEN))
                            .header(CsrfFilter.CSRF_HEADER_NAME, VALID_TOKEN)
                            .build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("PUT with matching cookie and header returns 200")
        void validPutPassesThrough() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.put("/api/v1/guests/123")
                            .cookie(new HttpCookie(CsrfFilter.CSRF_COOKIE_NAME, VALID_TOKEN))
                            .header(CsrfFilter.CSRF_HEADER_NAME, VALID_TOKEN)
                            .build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("DELETE with matching cookie and header returns 200")
        void validDeletePassesThrough() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.delete("/api/v1/invoices/xyz")
                            .cookie(new HttpCookie(CsrfFilter.CSRF_COOKIE_NAME, VALID_TOKEN))
                            .header(CsrfFilter.CSRF_HEADER_NAME, VALID_TOKEN)
                            .build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("PATCH with matching cookie and header returns 200")
        void validPatchPassesThrough() {
            final MockServerWebExchange exchange = run(
                    MockServerHttpRequest.method(Objects.requireNonNull(HttpMethod.PATCH), "/api/v1/stays/123")
                            .cookie(new HttpCookie(CsrfFilter.CSRF_COOKIE_NAME, VALID_TOKEN))
                            .header(CsrfFilter.CSRF_HEADER_NAME, VALID_TOKEN)
                            .build());
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ── Filter order ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Filter ordering")
    class FilterOrderTests {

        @Test
        @DisplayName("getOrder returns HIGHEST_PRECEDENCE + 2 (after SecurityHeadersFilter)")
        void shouldHaveCorrectOrder() {
            assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 2);
        }
    }
}
