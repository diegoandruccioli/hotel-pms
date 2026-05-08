package com.hotelpms.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SecurityHeadersFilter}.
 *
 * <p>
 * No Spring context is loaded. The filter is constructed directly and exercised
 * through a {@link MockServerWebExchange} so that each test remains fast and
 * fully isolated. The chain lambda calls {@code response.setComplete()} which
 * triggers the {@code beforeCommit} callbacks registered by the filter, making
 * the injected headers observable on the mock response object.
 */
class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Runs the filter against a minimal GET exchange, verifies the Mono completes
     * without error, and returns the response headers for assertions.
     */
    private HttpHeaders executeFilter() {
        final MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        StepVerifier.create(
                filter.filter(exchange, ex -> {
                    ex.getResponse().setStatusCode(HttpStatus.OK);
                    return ex.getResponse().setComplete();
                })
        ).verifyComplete();

        return exchange.getResponse().getHeaders();
    }

    // ── Security header injection ─────────────────────────────────────────────

    @Nested
    @DisplayName("Security header injection")
    class HeaderInjectionTests {

        @Test
        @DisplayName("should set Strict-Transport-Security on every response")
        void shouldSetHsts() {
            assertThat(executeFilter().getFirst(SecurityHeadersFilter.HEADER_HSTS))
                    .isEqualTo(SecurityHeadersFilter.VALUE_HSTS);
        }

        @Test
        @DisplayName("should set X-Content-Type-Options: nosniff on every response")
        void shouldSetXcto() {
            assertThat(executeFilter().getFirst(SecurityHeadersFilter.HEADER_XCTO))
                    .isEqualTo(SecurityHeadersFilter.VALUE_XCTO);
        }

        @Test
        @DisplayName("should set X-Frame-Options: DENY on every response")
        void shouldSetXfo() {
            assertThat(executeFilter().getFirst(SecurityHeadersFilter.HEADER_XFO))
                    .isEqualTo(SecurityHeadersFilter.VALUE_XFO);
        }

        @Test
        @DisplayName("should set Referrer-Policy: no-referrer on every response")
        void shouldSetReferrerPolicy() {
            assertThat(executeFilter().getFirst(SecurityHeadersFilter.HEADER_REFERRER))
                    .isEqualTo(SecurityHeadersFilter.VALUE_REFERRER);
        }

        @Test
        @DisplayName("should set Permissions-Policy disabling camera/microphone/geolocation")
        void shouldSetPermissionsPolicy() {
            assertThat(executeFilter().getFirst(SecurityHeadersFilter.HEADER_PERMISSIONS))
                    .isEqualTo(SecurityHeadersFilter.VALUE_PERMISSIONS);
        }

        @Test
        @DisplayName("should set Content-Security-Policy: default-src 'none' for API responses")
        void shouldSetCsp() {
            assertThat(executeFilter().getFirst(SecurityHeadersFilter.HEADER_CSP))
                    .isEqualTo(SecurityHeadersFilter.VALUE_CSP);
        }
    }

    // ── Filter order ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Filter order")
    class FilterOrderTests {

        @Test
        @DisplayName("getOrder should return HIGHEST_PRECEDENCE + 1 for front-of-chain placement")
        void shouldHaveNearHighestPrecedenceOrder() {
            assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
        }
    }
}
