package com.hotelpms.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimiterConfig}.
 *
 * <p>Verifies that both {@code remoteAddrKeyResolver} and {@code userKeyResolver}
 * produce the correct bucket key under each code path — proxy header present,
 * proxy header absent, and user header present.  No Spring context is loaded.
 */
class RateLimiterConfigTest {

    private RateLimiterConfig config;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
    }

    // ── remoteAddrKeyResolver ─────────────────────────────────────────────────

    @Nested
    @DisplayName("remoteAddrKeyResolver")
    class RemoteAddrKeyResolverTests {

        @Test
        @DisplayName("uses leftmost IP from X-Forwarded-For when header is present")
        void usesForwardedForFirstIp() {
            final KeyResolver resolver = config.remoteAddrKeyResolver();
            final MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/v1/auth/login")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 0))
                    .header("X-Forwarded-For", "203.0.113.5, 10.0.0.1")
                    .build();
            final MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(resolver.resolve(exchange))
                    .assertNext(key -> assertThat(key).isEqualTo("203.0.113.5"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("falls back to RemoteAddress when X-Forwarded-For is absent")
        void fallsBackToRemoteAddressWhenHeaderAbsent() {
            final KeyResolver resolver = config.remoteAddrKeyResolver();
            final MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/v1/auth/login")
                    .remoteAddress(new InetSocketAddress("192.168.1.42", 0))
                    .build();
            final MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(resolver.resolve(exchange))
                    .assertNext(key -> assertThat(key).isEqualTo("192.168.1.42"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("falls back to RemoteAddress when X-Forwarded-For is blank")
        void fallsBackToRemoteAddressWhenHeaderBlank() {
            final KeyResolver resolver = config.remoteAddrKeyResolver();
            final MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/v1/auth/login")
                    .remoteAddress(new InetSocketAddress("192.168.1.99", 0))
                    .header("X-Forwarded-For", "   ")
                    .build();
            final MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(resolver.resolve(exchange))
                    .assertNext(key -> assertThat(key).isEqualTo("192.168.1.99"))
                    .verifyComplete();
        }
    }

    // ── userKeyResolver ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("userKeyResolver")
    class UserKeyResolverTests {

        @Test
        @DisplayName("uses 'user:<username>' when X-Auth-User header is present")
        void usesAuthUserHeader() {
            final KeyResolver resolver = config.userKeyResolver();
            final MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/v1/guests")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 0))
                    .header("X-Auth-User", "alice")
                    .build();
            final MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(resolver.resolve(exchange))
                    .assertNext(key -> assertThat(key).isEqualTo("user:alice"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("falls back to 'ip:<xff>' when X-Auth-User absent and X-Forwarded-For present")
        void fallsBackToForwardedForWhenUserAbsent() {
            final KeyResolver resolver = config.userKeyResolver();
            final MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/v1/reservations")
                    .remoteAddress(new InetSocketAddress("10.0.0.1", 0))
                    .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                    .build();
            final MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(resolver.resolve(exchange))
                    .assertNext(key -> assertThat(key).isEqualTo("ip:203.0.113.7"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("falls back to 'ip:<remoteAddr>' when both headers are absent")
        void fallsBackToRemoteAddressWhenBothHeadersAbsent() {
            final KeyResolver resolver = config.userKeyResolver();
            final MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/v1/stays")
                    .remoteAddress(new InetSocketAddress("172.16.0.99", 0))
                    .build();
            final MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(resolver.resolve(exchange))
                    .assertNext(key -> assertThat(key).isEqualTo("ip:172.16.0.99"))
                    .verifyComplete();
        }
    }
}
