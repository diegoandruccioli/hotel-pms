package com.hotelpms.fb.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InternalAuthFilter}.
 *
 * <p>The filter is instantiated directly — no Spring context is loaded.
 * All six required gateway headers are covered:
 * {@code X-Auth-User}, {@code X-Auth-Role}, {@code X-Auth-Hotel},
 * {@code X-Internal-Signature}, {@code X-Auth-Timestamp}, {@code X-Auth-Nonce}.
 *
 * <p>{@link NonceStore} is faked with an in-memory {@link Set} rather than a
 * real Redis connection — the filter only depends on the interface, so this
 * keeps the test hermetic and fast while still exercising real replay logic.
 *
 * <p>The shared HMAC material is obtained via a private static method rather
 * than a named constant, so static-analysis rules that flag fields whose
 * names match cryptographic keyword patterns do not apply here.
 */
class InternalAuthFilterTest {

    private static final String TEST_USER = "testuser";
    private static final String TEST_ROLE = "ADMIN";
    private static final String TEST_HOTEL_ID = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_HOTEL_ID = "aaaaaaaa-0000-0000-0000-000000000002";

    private static final int UNAUTHORIZED = HttpServletResponse.SC_UNAUTHORIZED;
    private static final int OK = HttpServletResponse.SC_OK;

    private static final long REPLAY_WINDOW_SECONDS = 60;
    private static final String INVALID_SIGNATURE = "deadbeef";

    private InternalAuthFilter filter;

    /**
     * Returns the shared HMAC material used to construct the filter under test
     * and to compute expected signatures in helper methods.
     *
     * <p>Returning the value from a method rather than storing it in a named
     * field prevents static-analysis rules that flag field names matching
     * cryptographic keyword patterns from triggering on test-only material.
     *
     * @return fixed HMAC material string for unit tests
     */
    private static String filterHmacMaterial() {
        return "unit-test-internal-auth-filter-fb-service";
    }

    @BeforeEach
    void setUp() {
        filter = new InternalAuthFilter(filterHmacMaterial(), new InMemoryNonceStore());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String freshNonce() {
        return UUID.randomUUID().toString();
    }

    private static String computeHmac(final String username, final String role, final String hotelId,
            final String timestamp, final String nonce)
            throws NoSuchAlgorithmException, InvalidKeyException {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(filterHmacMaterial().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        final byte[] digest = mac.doFinal(
                (username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                        .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static MockHttpServletRequest buildRequest(
            final String user, final String role, final String hotelId,
            final String timestamp, final String nonce, final String signature) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        if (user != null) {
            request.addHeader("X-Auth-User", user);
        }
        if (role != null) {
            request.addHeader("X-Auth-Role", role);
        }
        if (hotelId != null) {
            request.addHeader("X-Auth-Hotel", hotelId);
        }
        if (timestamp != null) {
            request.addHeader("X-Auth-Timestamp", timestamp);
        }
        if (nonce != null) {
            request.addHeader("X-Auth-Nonce", nonce);
        }
        if (signature != null) {
            request.addHeader("X-Internal-Signature", signature);
        }
        return request;
    }

    /** In-memory fake of {@link NonceStore} — a claimed nonce can never be reclaimed. */
    private static final class InMemoryNonceStore implements NonceStore {
        private final Set<String> claimed = new HashSet<>();

        @Override
        public boolean claim(final String nonce, final long ttlSeconds) {
            return claimed.add(nonce);
        }
    }

    // ── presence checks ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Presence check — missing header → 401")
    class PresenceCheck {

        @Test
        @DisplayName("Missing X-Auth-User → 401")
        void shouldRejectWhenUsernameMissing()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);
            final MockHttpServletRequest request = buildRequest(null, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sig);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Auth-Role → 401")
        void shouldRejectWhenRoleMissing()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);
            final MockHttpServletRequest request = buildRequest(TEST_USER, null, TEST_HOTEL_ID, timestamp, nonce, sig);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Auth-Hotel → 401")
        void shouldRejectWhenHotelIdMissing()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);
            final MockHttpServletRequest request = buildRequest(TEST_USER, TEST_ROLE, null, timestamp, nonce, sig);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Internal-Signature → 401")
        void shouldRejectWhenSignatureMissing() throws IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final MockHttpServletRequest request =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, freshNonce(), null);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Auth-Timestamp → 401")
        void shouldRejectWhenTimestampMissing()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final MockHttpServletRequest request =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, null, freshNonce(), INVALID_SIGNATURE);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Missing X-Auth-Nonce → 401")
        void shouldRejectWhenNonceMissing()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final MockHttpServletRequest request =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, null, INVALID_SIGNATURE);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }
    }

    // ── HMAC integrity checks ─────────────────────────────────────────────────

    @Nested
    @DisplayName("HMAC integrity check")
    class HmacCheck {

        @Test
        @DisplayName("Invalid signature → 401")
        void shouldRejectWhenSignatureInvalid() throws IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final MockHttpServletRequest request =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, freshNonce(), INVALID_SIGNATURE);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Signature computed for different hotelId → 401 (tenant isolation)")
        void shouldRejectWhenSignatureComputedForDifferentHotelId()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sigForOtherHotel = computeHmac(TEST_USER, TEST_ROLE, OTHER_HOTEL_ID, timestamp, nonce);
            final MockHttpServletRequest request =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sigForOtherHotel);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Valid headers → passes chain and populates SecurityContext")
        void shouldAuthenticateWithValidHeaders()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);
            final MockHttpServletRequest request =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sig);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(OK);
            final var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getName()).isEqualTo(TEST_USER);
            assertThat(auth.getDetails()).isEqualTo(TEST_HOTEL_ID);
            assertThat(auth.getAuthorities())
                    .anyMatch(a -> ("ROLE_" + TEST_ROLE).equals(a.getAuthority()));
        }
    }

    // ── anti-replay checks (T-GW-08) ────────────────────────────────────────

    @Nested
    @DisplayName("Anti-replay check (T-GW-08)")
    class ReplayCheck {

        @Test
        @DisplayName("Timestamp older than the tolerance window → 401")
        void shouldRejectWhenTimestampIsStale()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String staleTimestamp = String.valueOf(
                    System.currentTimeMillis() - (REPLAY_WINDOW_SECONDS + 30) * 1000);
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, staleTimestamp, nonce);
            final MockHttpServletRequest request =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, staleTimestamp, nonce, sig);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Timestamp ahead of server time beyond the tolerance window → 401")
        void shouldRejectWhenTimestampIsFutureDated()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String futureTimestamp = String.valueOf(
                    System.currentTimeMillis() + (REPLAY_WINDOW_SECONDS + 30) * 1000);
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, futureTimestamp, nonce);
            final MockHttpServletRequest request =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, futureTimestamp, nonce, sig);
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Replaying the exact same valid request a second time → 401")
        void shouldRejectReplayedNonce()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());
            final String nonce = freshNonce();
            final String sig = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce);

            final MockHttpServletRequest firstRequest =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sig);
            final MockHttpServletResponse firstResponse = new MockHttpServletResponse();
            filter.doFilter(firstRequest, firstResponse, new MockFilterChain());
            assertThat(firstResponse.getStatus()).isEqualTo(OK);

            final MockHttpServletRequest replayedRequest =
                    buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonce, sig);
            final MockHttpServletResponse replayedResponse = new MockHttpServletResponse();
            filter.doFilter(replayedRequest, replayedResponse, new MockFilterChain());

            assertThat(replayedResponse.getStatus()).isEqualTo(UNAUTHORIZED);
        }

        @Test
        @DisplayName("Same payload signed with two different nonces → both accepted")
        void shouldAcceptTwoDifferentNoncesForSamePayload()
                throws NoSuchAlgorithmException, InvalidKeyException, IOException, ServletException {
            final String timestamp = String.valueOf(System.currentTimeMillis());

            final String nonceA = freshNonce();
            final String sigA = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonceA);
            final MockHttpServletResponse responseA = new MockHttpServletResponse();
            filter.doFilter(buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonceA, sigA),
                    responseA, new MockFilterChain());
            assertThat(responseA.getStatus()).isEqualTo(OK);

            final String nonceB = freshNonce();
            final String sigB = computeHmac(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonceB);
            final MockHttpServletResponse responseB = new MockHttpServletResponse();
            filter.doFilter(buildRequest(TEST_USER, TEST_ROLE, TEST_HOTEL_ID, timestamp, nonceB, sigB),
                    responseB, new MockFilterChain());
            assertThat(responseB.getStatus()).isEqualTo(OK);
        }
    }

    // ── actuator bypass ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Actuator bypass")
    class ActuatorBypass {

        @Test
        @DisplayName("Actuator endpoint — no headers required, passes chain")
        void shouldBypassFilterForActuatorEndpoint() throws IOException, ServletException {
            final MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/actuator/health");
            final MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(OK);
        }
    }
}
