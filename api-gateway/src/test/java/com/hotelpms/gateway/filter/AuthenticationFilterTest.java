package com.hotelpms.gateway.filter;

import com.hotelpms.gateway.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.security.Key;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link AuthenticationFilter}.
 *
 * <p>
 * No Spring context is loaded. {@link JwtUtil} and {@link AuthenticationFilter}
 * are constructed manually with a fixed test secret, which eliminates:
 * <ul>
 * <li>The {@code ${INTERNAL_HMAC_SECRET}} placeholder resolution failure.</li>
 * <li>Any attempt to connect to Redis at test startup.</li>
 * <li>Slow Spring context initialisation overhead.</li>
 * </ul>
 *
 * <p>
 * The {@link GatewayFilterChain} mock is <em>not</em> stubbed globally in
 * {@code @BeforeEach} because Mockito's strict mode raises an
 * {@code UnnecessaryStubbingException} for tests that return 401 before ever
 * reaching the chain. Instead, each test that needs the chain to proceed stubs
 * it locally.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

        /**
         * 32-byte Base64-encoded secret identical to the {@link JwtUtil} default
         * {@code jwt.secret} fallback — keeps tests hermetic.
         */
        private static final String JWT_SECRET_B64 = "bXktMzItYnl0ZS1zZWNyZXQta2V5LWZvci10ZXN0LWxvY2FsLWRldi0xMjM0NQ==";

        /** Fixed HMAC shared secret used for X-Internal-Signature in tests. */
        private static final String HMAC_SECRET = "test-internal-hmac-secret-for-unit-tests";

        private static final long ONE_HOUR_MS = 3_600_000L;

        private AuthenticationFilter authenticationFilter;
        private AuthenticationFilter.Config config;

        // chainMock is declared here but intentionally NOT stubbed in @BeforeEach.
        // Tests that need the chain to proceed stub it locally to avoid
        // UnnecessaryStubbingException from Mockito's strict-stubs mode.
        private GatewayFilterChain chainMock;

        @BeforeEach
        void setUp() throws Exception {
                final JwtUtil jwtUtil = new JwtUtil();
                setPrivateField(jwtUtil, "secret", JWT_SECRET_B64);
                jwtUtil.init();

                authenticationFilter = new AuthenticationFilter(jwtUtil, HMAC_SECRET);
                config = new AuthenticationFilter.Config();
                chainMock = mock(GatewayFilterChain.class);
        }

        // -----------------------------------------------------------------------
        // Helper: build a signed JWT
        // -----------------------------------------------------------------------

        private String buildJwt(final long ttlMillis, final String username, final String role) {
                final Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET_B64));
                final Date now = new Date();
                final Date expiration = new Date(now.getTime() + ttlMillis);
                return Jwts.builder()
                                .setSubject(username)
                                .claim("role", role)
                                .setIssuedAt(now)
                                .setExpiration(expiration)
                                .signWith(key, SignatureAlgorithm.HS256)
                                .compact();
        }

        /**
         * Reflectively sets a private field — avoids needing Spring for @Value
         * injection.
         */
        private static void setPrivateField(final Object target,
                        final String fieldName,
                        final Object value) throws Exception {
                final Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
        }

        // -----------------------------------------------------------------------
        // Nested test groups
        // -----------------------------------------------------------------------

        @Nested
        @DisplayName("Valid JWT")
        class ValidJwtTests {

                @Test
                @DisplayName("should pass the request to the downstream chain when a valid Bearer token is supplied")
                void shouldPassWhenValidToken() {
                        // Stub locally – this test IS expected to reach the chain.
                        when(chainMock.filter(any())).thenReturn(Mono.empty());

                        final String validToken = buildJwt(ONE_HOUR_MS, "admin", "ADMIN");
                        final MockServerWebExchange exchange = MockServerWebExchange.from(
                                        MockServerHttpRequest.get("/api/v1/guests")
                                                        .cookie(new HttpCookie("jwt", validToken))
                                                        .build());

                        StepVerifier.create(authenticationFilter.apply(config).filter(exchange, chainMock))
                                        .verifyComplete();

                        verify(chainMock).filter(any());
                }

                @Test
                @DisplayName("should inject X-Auth-User, X-Auth-Role, and X-Internal-Signature on valid token")
                void shouldInjectAuthHeadersOnValidToken() {
                        final String validToken = buildJwt(ONE_HOUR_MS, "receptionist1", "RECEPTIONIST");

                        final MockServerWebExchange exchange = MockServerWebExchange.from(
                                        MockServerHttpRequest.get("/api/v1/stays")
                                                        .cookie(new HttpCookie("jwt", validToken))
                                                        .build());

                        // Use a lambda chain that captures the mutated request.
                        // No Mockito stubbing needed here → no UnnecessaryStubbingException risk.
                        final AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
                        final GatewayFilterChain capturingChain = ex -> {
                                captured.set(ex.getRequest());
                                return Mono.empty();
                        };

                        authenticationFilter.apply(config).filter(exchange, capturingChain).block();

                        assertThat(captured.get()).isNotNull();
                        assertThat(captured.get().getHeaders().getFirst("X-Auth-User"))
                                        .isEqualTo("receptionist1");
                        assertThat(captured.get().getHeaders().getFirst("X-Auth-Role"))
                                        .isEqualTo("RECEPTIONIST");
                        assertThat(captured.get().getHeaders().getFirst("X-Internal-Signature"))
                                        .isNotBlank();
                }
        }

        @Nested
        @DisplayName("Missing Cookie")
        class MissingCookieTests {

                @Test
                @DisplayName("should return 401 when jwt cookie is absent")
                void shouldReturn401WhenCookieMissing() {
                        final MockServerWebExchange exchange = MockServerWebExchange.from(
                                        MockServerHttpRequest.get("/api/v1/guests").build());

                        StepVerifier.create(authenticationFilter.apply(config).filter(exchange, chainMock))
                                        .verifyComplete();

                        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                }

                @Test
                @DisplayName("should return 401 when jwt cookie value is empty")
                void shouldReturn401WhenCookieEmpty() {
                        final MockServerWebExchange exchange = MockServerWebExchange.from(
                                        MockServerHttpRequest.get("/api/v1/guests")
                                                        .cookie(new HttpCookie("jwt", ""))
                                                        .build());

                        StepVerifier.create(authenticationFilter.apply(config).filter(exchange, chainMock))
                                        .verifyComplete();

                        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                }
        }

        @Nested
        @DisplayName("Invalid / malformed JWT")
        class InvalidJwtTests {

                @Test
                @DisplayName("should return 401 when the token is a random string (not a JWT)")
                void shouldReturn401WhenTokenIsGarbage() {
                        final MockServerWebExchange exchange = MockServerWebExchange.from(
                                        MockServerHttpRequest.get("/api/v1/stays")
                                                        .cookie(new HttpCookie("jwt", "this.is.not.a.jwt"))
                                                        .build());

                        StepVerifier.create(authenticationFilter.apply(config).filter(exchange, chainMock))
                                        .verifyComplete();

                        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                }

                @Test
                @DisplayName("should return 401 when the token is signed with a different HMAC secret")
                void shouldReturn401WhenSignedWithWrongSecret() {
                        final Key wrongKey = Keys.hmacShaKeyFor(
                                        Decoders.BASE64.decode(
                                                        "d3Jvbmctc2VjcmV0LWtleS0zMi1ieXRlcy0xMjM0NTY3OA=="));
                        final String tampered = Jwts.builder()
                                        .setSubject("hacker")
                                        .claim("role", "ADMIN")
                                        .setExpiration(new Date(System.currentTimeMillis() + ONE_HOUR_MS))
                                        .signWith(wrongKey, SignatureAlgorithm.HS256)
                                        .compact();

                        final MockServerWebExchange exchange = MockServerWebExchange.from(
                                        MockServerHttpRequest.get("/api/v1/billing")
                                                        .cookie(new HttpCookie("jwt", tampered))
                                                        .build());

                        StepVerifier.create(authenticationFilter.apply(config).filter(exchange, chainMock))
                                        .verifyComplete();

                        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                }
        }

        @Nested
        @DisplayName("Expired JWT")
        class ExpiredJwtTests {

                @Test
                @DisplayName("should return 401 when the token is expired")
                void shouldReturn401WhenTokenIsExpired() {
                        // TTL of -1 ms → already expired by the time jjwt validates it
                        final String expiredToken = buildJwt(-1L, "admin", "ADMIN");

                        final MockServerWebExchange exchange = MockServerWebExchange.from(
                                        MockServerHttpRequest.get("/api/v1/reservations")
                                                        .cookie(new HttpCookie("jwt", expiredToken))
                                                        .build());

                        StepVerifier.create(authenticationFilter.apply(config).filter(exchange, chainMock))
                                        .verifyComplete();

                        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                }
        }
}
