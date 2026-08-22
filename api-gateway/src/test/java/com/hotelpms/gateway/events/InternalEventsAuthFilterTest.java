package com.hotelpms.gateway.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InternalEventsAuthFilter}, mirroring
 * {@code InternalAuthFilterTest} (internal-auth-lib) so the two verification
 * algorithms are checked against the same scenarios despite living on
 * different stacks (servlet vs reactive) — see the class javadoc for why
 * they can't share code.
 */
@ExtendWith(MockitoExtension.class)
class InternalEventsAuthFilterTest {

    private static final String TEST_USER = "testuser";
    private static final String TEST_ROLE = "ADMIN";
    private static final String TEST_HOTEL_ID = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_HOTEL_ID = "aaaaaaaa-0000-0000-0000-000000000002";
    private static final String NOTIFY_PATH = "/internal/events/notify";
    private static final String INVALID_SIGNATURE = "deadbeef";
    private static final long REPLAY_WINDOW_SECONDS = 60;

    private static String filterHmacMaterial() {
        return "unit-test-internal-events-auth-filter";
    }

    private InternalEventsAuthFilter filter;
    private ReactiveStringRedisTemplate redisTemplate;
    private final Set<String> claimedNonces = new HashSet<>();

    private ReactiveValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        final ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        valueOps = ops;
        filter = new InternalEventsAuthFilter(filterHmacMaterial(), redisTemplate);
        claimedNonces.clear();
    }

    /**
     * Stubs the reactive nonce store's SETNX-equivalent — only called by tests
     * that reach the anti-replay check, so tests that reject earlier (presence
     * check, HMAC check, path guard) don't trip Mockito's strict-stubs
     * {@code UnnecessaryStubbingException}, same convention as {@code AuthenticationFilterTest}.
     */
    private void stubNonceClaim() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> Mono.just(claimedNonces.add(invocation.getArgument(0))));
    }

    private static String freshNonce() {
        return UUID.randomUUID().toString();
    }

    private static String computeHmac(final String username, final String role, final String hotelId,
            final String timestamp, final String nonce) throws NoSuchAlgorithmException, InvalidKeyException {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(filterHmacMaterial().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        final byte[] digest = mac.doFinal(
                (username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                        .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static MockServerWebExchange buildExchange(final String path,
            final String user, final String role, final String hotelId,
            final String timestamp, final String nonce, final String signature) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post(path);
        if (user != null) {
            builder = builder.header("X-Auth-User", user);
        }
        if (role != null) {
            builder = builder.header("X-Auth-Role", role);
        }
        if (hotelId != null) {
            builder = builder.header("X-Auth-Hotel", hotelId);
        }
        if (timestamp != null) {
            builder = builder.header("X-Auth-Timestamp", timestamp);
        }
        if (nonce != null) {
            builder = builder.header("X-Auth-Nonce", nonce);
        }
        if (signature != null) {
            builder = builder.header("X-Internal-Signature", signature);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private static MockServerWebExchange buildExchange(
            final String user, final String role, final String hotelId,
            final String timestamp, final String nonce, final String signature) {
        return buildExchange(NOTIFY_PATH, user, role, hotelId, timestamp, nonce, signature);
    }

    @Nested
    @DisplayName("Path guard — only /internal/** is verified")
    class PathGuard {

        @Test
        @DisplayName("A request outside /internal is passed through unverified (AuthenticationFilter's concern, not this one)")
        void passesThroughNonInternalPaths() {
            final WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());
            final MockServerWebExchange exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/events/stream").build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("A path that merely starts with '/internal' as a substring, without a segment boundary, "
                + "is NOT matched by the guard — passed through unverified, same as any other non-/internal path")
        void doesNotMatchSubstringLookalikeAsGuarded() {
            final WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());
            final MockServerWebExchange exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.post("/internal-evil").build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("A request to exactly /internal/events/notify without valid headers is verified and rejected")
        void enforcesOnTheGuardedNotifyPath() {
            final MockServerWebExchange exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.post(NOTIFY_PATH).build());

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Presence check — missing or blank header -> 401")
    class PresenceCheck {

        @Test
        @DisplayName("Missing X-Auth-User -> 401")
        void rejectsWhenUsernameMissing() throws Exception {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);
            final MockServerWebExchange exchange = buildExchange(null, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sig);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Auth-Role -> 401")
        void rejectsWhenRoleMissing() throws Exception {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);
            final MockServerWebExchange exchange = buildExchange(TEST_USER, null, TEST_HOTEL_ID, timestamp, nonce, sig);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Auth-Timestamp -> 401")
        void rejectsWhenTimestampMissing() {
            final MockServerWebExchange exchange =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, null, freshNonce(), INVALID_SIGNATURE);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Auth-Nonce -> 401")
        void rejectsWhenNonceMissing() {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final MockServerWebExchange exchange =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, null, INVALID_SIGNATURE);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Auth-Hotel -> 401")
        void rejectsWhenHotelIdMissing() throws Exception {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);
            final MockServerWebExchange exchange = buildExchange(TEST_USER, TEST_ROLE, null, timestamp, nonce, sig);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Internal-Signature -> 401")
        void rejectsWhenSignatureMissing() {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final MockServerWebExchange exchange =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, freshNonce(), null);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("HMAC integrity check")
    class HmacCheck {

        @Test
        @DisplayName("Invalid signature -> 401")
        void rejectsWhenSignatureInvalid() {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final MockServerWebExchange exchange =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, freshNonce(), INVALID_SIGNATURE);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Signature computed for a different hotelId -> 401 (tenant isolation)")
        void rejectsWhenSignatureComputedForDifferentHotelId() throws Exception {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sigForOtherHotel = computeHmac(TEST_USER, TEST_ROLE, OTHER_HOTEL_ID, timestamp, nonce);
            final MockServerWebExchange exchange =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sigForOtherHotel);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Valid headers -> passes chain and stashes the verified hotelId as an exchange attribute")
        void passesChainWithValidHeaders() throws Exception {
            stubNonceClaim();
            final WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);
            final MockServerWebExchange exchange =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sig);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            final String hotelIdAttribute = exchange.getAttribute(InternalEventsAuthFilter.class.getName() + ".hotelId");
            assertThat(hotelIdAttribute).isEqualTo(TEST_HOTEL_ID);
        }
    }

    @Nested
    @DisplayName("Anti-replay check (T-GW-08)")
    class ReplayCheck {

        @Test
        @DisplayName("Timestamp older than the tolerance window -> 401")
        void rejectsStaleTimestamp() throws Exception {
            final String staleTimestamp = String.valueOf(
                    System.currentTimeMillis() - (REPLAY_WINDOW_SECONDS + 30) * 1000);
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, staleTimestamp, nonce);
            final MockServerWebExchange exchange =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, staleTimestamp, nonce, sig);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Timestamp ahead of server time beyond the tolerance window -> 401")
        void rejectsFutureDatedTimestamp() throws Exception {
            final String futureTimestamp = String.valueOf(
                    System.currentTimeMillis() + (REPLAY_WINDOW_SECONDS + 30) * 1000);
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, futureTimestamp, nonce);
            final MockServerWebExchange exchange =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, futureTimestamp, nonce, sig);

            StepVerifier.create(filter.filter(exchange, mock(WebFilterChain.class))).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Same payload signed with two different nonces -> both accepted")
        void acceptsTwoDifferentNoncesForSamePayload() throws Exception {
            stubNonceClaim();
            final WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());
            final String timestamp = String.valueOf(System.currentTimeMillis());

            final String nonceA = freshNonce();
            final String sigA = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonceA);
            final MockServerWebExchange exchangeA =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonceA, sigA);
            StepVerifier.create(filter.filter(exchangeA, chain)).verifyComplete();
            assertThat(exchangeA.getResponse().getStatusCode()).isNull();

            final String nonceB = freshNonce();
            final String sigB = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonceB);
            final MockServerWebExchange exchangeB =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonceB, sigB);
            StepVerifier.create(filter.filter(exchangeB, chain)).verifyComplete();
            assertThat(exchangeB.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("Replaying the exact same valid request a second time -> 401")
        void rejectsReplayedNonce() throws Exception {
            stubNonceClaim();
            final WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);

            final MockServerWebExchange first =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sig);
            StepVerifier.create(filter.filter(first, chain)).verifyComplete();
            assertThat(first.getResponse().getStatusCode()).isNull();

            final MockServerWebExchange replayed =
                    buildExchange(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sig);
            StepVerifier.create(filter.filter(replayed, mock(WebFilterChain.class))).verifyComplete();

            assertThat(replayed.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
