package com.hotelpms.frontdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Validates internal gateway headers injected by the API Gateway after JWT
 * verification.
 *
 * <p>
 * The filter enforces a three-layer check:
 * <ol>
 * <li><b>Presence check</b> – rejects requests missing any of the required
 * headers: {@code X-Auth-User}, {@code X-Auth-Role}, {@code X-Auth-Hotel},
 * {@code X-Internal-Signature}, {@code X-Auth-Timestamp}, or
 * {@code X-Auth-Nonce}.</li>
 * <li><b>HMAC-SHA256 integrity check</b> – recomputes
 * {@code HMAC-SHA256(sharedSecret, "username:role:hotelId:timestamp:nonce")}
 * and compares it against the received signature using constant-time
 * {@link MessageDigest#isEqual} to prevent timing-based side-channel
 * attacks. Requests with an absent or mismatched signature are rejected with
 * {@code 401 Unauthorized}.</li>
 * <li><b>Anti-replay check</b> (T-GW-08) – rejects signatures whose timestamp
 * has drifted outside a short tolerance window, and rejects any nonce already
 * claimed within that window via {@link NonceStore}. Without this, a captured
 * header set would remain a valid credential indefinitely.</li>
 * </ol>
 *
 * <p>
 * Only the API Gateway knows the shared secret and can produce a valid
 * signature,
 * making it impossible for any internal caller to spoof {@code X-Auth-User} /
 * {@code X-Auth-Role} headers without intercepting the secret.
 */
public final class InternalAuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(InternalAuthFilter.class);

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HEADER_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HEADER_NONCE = "X-Auth-Nonce";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** Maximum allowed drift between the signed timestamp and server time (T-GW-08). */
    private static final long REPLAY_WINDOW_SECONDS = 60;
    /** Nonce claims are remembered for twice the replay window, as a safety margin. */
    private static final long NONCE_TTL_SECONDS = REPLAY_WINDOW_SECONDS * 2;

    private final String hmacSecret;
    private final NonceStore nonceStore;

    /**
     * Constructs the filter with the shared HMAC secret injected from
     * configuration and the nonce store used for replay detection.
     *
     * @param hmacSecret the shared secret, injected from
     *                   {@code internal.hmac.secret};
     *                   must match exactly the value configured in the API Gateway
     * @param nonceStore the store used to detect re-used nonces (T-GW-08)
     */
    public InternalAuthFilter(@Value("${internal.hmac.secret}") final String hmacSecret,
            final NonceStore nonceStore) {
        this.hmacSecret = hmacSecret;
        this.nonceStore = nonceStore;
    }

    /**
     * Skips HMAC validation for actuator endpoints so Docker health checks
     * and Prometheus scraping can reach them without gateway headers.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull final HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain) throws ServletException, IOException {

        final String username = request.getHeader(HEADER_USER);
        final String role = request.getHeader(HEADER_ROLE);
        final String hotelId = request.getHeader(HEADER_HOTEL);
        final String signature = request.getHeader(HEADER_SIGNATURE);
        final String timestamp = request.getHeader(HEADER_TIMESTAMP);
        final String nonce = request.getHeader(HEADER_NONCE);

        if (!StringUtils.hasText(username) || !StringUtils.hasText(role)
                || !StringUtils.hasText(hotelId) || !StringUtils.hasText(signature)
                || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            rejectRequest(response, "Missing required gateway authentication headers");
            return;
        }

        if (!isSignatureValid(username, role, hotelId, timestamp, nonce, signature)) {
            LOG.warn("[InternalAuthFilter] HMAC signature mismatch for user={}", username);
            rejectRequest(response, "Invalid internal request signature");
            return;
        }

        if (!isTimestampFresh(timestamp)) {
            LOG.warn("[InternalAuthFilter] Stale or future-dated timestamp for user={}", username);
            rejectRequest(response, "Stale or future-dated request signature");
            return;
        }

        if (!nonceStore.claim(nonce, NONCE_TTL_SECONDS)) {
            LOG.warn("[InternalAuthFilter] Replayed nonce detected for user={}", username);
            rejectRequest(response, "Request signature already used");
            return;
        }

        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        // Store hotelId as details so the service layer can extract tenant context
        // without coupling the controller to the security mechanism.
        auth.setDetails(hotelId);

        SecurityContextHolder.getContext().setAuthentication(auth);
        final String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Verifies the received HMAC-SHA256 signature against a locally computed
     * digest.
     *
     * <p>
     * The comparison uses {@link MessageDigest#isEqual} for constant-time
     * evaluation,
     * preventing timing-based side-channel attacks.
     *
     * @param username  the username from the {@code X-Auth-User} header
     * @param role      the role from the {@code X-Auth-Role} header
     * @param hotelId   the hotel UUID from the {@code X-Auth-Hotel} header
     * @param timestamp the epoch-millis timestamp from {@code X-Auth-Timestamp}
     * @param nonce     the nonce from {@code X-Auth-Nonce}
     * @param signature the hex signature from the {@code X-Internal-Signature}
     *                  header
     * @return {@code true} if the recomputed digest matches the received signature
     */
    private boolean isSignatureValid(final String username, final String role,
            final String hotelId, final String timestamp, final String nonce, final String signature) {
        try {
            final String expected = computeHmac(username, role, hotelId, timestamp, nonce);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (final IllegalStateException e) {
            LOG.error("[InternalAuthFilter] Failed to compute HMAC for signature verification", e);
            return false;
        }
    }

    /**
     * Checks that the signed timestamp is within {@link #REPLAY_WINDOW_SECONDS}
     * of the current server time, in either direction (T-GW-08).
     *
     * @param timestamp the epoch-millis timestamp from {@code X-Auth-Timestamp}
     * @return {@code true} if the timestamp is parseable and within the
     *         tolerance window; {@code false} otherwise
     */
    private boolean isTimestampFresh(final String timestamp) {
        try {
            final long requestMillis = Long.parseLong(timestamp);
            final long driftMillis = Math.abs(System.currentTimeMillis() - requestMillis);
            return driftMillis <= REPLAY_WINDOW_SECONDS * 1000;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /**
     * Computes {@code HMAC-SHA256(secret, "username:role:hotelId:timestamp:nonce")}
     * and returns the result as a lowercase hex string, matching the API
     * Gateway's signing convention.
     *
     * <p>Including {@code hotelId} in the signed payload guarantees the integrity
     * of the {@code X-Auth-Hotel} tenant-isolation header, preventing any internal
     * caller from tampering with the hotel context without invalidating the HMAC.
     * Including {@code timestamp} and {@code nonce} (T-GW-08) ties the signature
     * to a single point in time, so it cannot be replayed once the timestamp
     * falls outside the tolerance window or its nonce has already been claimed.
     *
     * @param username  the authenticated username
     * @param role      the role associated with the user
     * @param hotelId   the hotel UUID associated with the user
     * @param timestamp the epoch-millis timestamp signed by the gateway
     * @param nonce     the random nonce signed by the gateway
     * @return hex-encoded HMAC digest
     * @throws IllegalStateException if the JVM does not support HmacSHA256
     */
    private String computeHmac(final String username, final String role, final String hotelId,
            final String timestamp, final String nonce) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            final SecretKeySpec keySpec = new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            final byte[] digest = mac.doFinal(
                    (username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC_SIGNATURE_FAILED", e);
        }
    }

    private void rejectRequest(final HttpServletResponse response, final String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
