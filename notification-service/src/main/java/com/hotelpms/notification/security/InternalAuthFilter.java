package com.hotelpms.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
 * Validates internal gateway headers on every inbound request (T-GW-08).
 *
 * <p>Three-layer check: (1) presence of required headers, (2) constant-time
 * HMAC-SHA256 signature verification, (3) anti-replay via timestamp window
 * and Redis-backed nonce claim.
 *
 * <p>Actuator endpoints are excluded so Docker health checks and Prometheus
 * scraping work without gateway headers.
 */
public final class InternalAuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(InternalAuthFilter.class);

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HEADER_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HEADER_NONCE = "X-Auth-Nonce";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final long REPLAY_WINDOW_SECONDS = 60;
    private static final long NONCE_TTL_SECONDS = REPLAY_WINDOW_SECONDS * 2;

    private final String hmacSecret;
    private final NonceStore nonceStore;

    /**
     * Constructs the filter with the shared HMAC secret and nonce store.
     *
     * @param hmacSecret the shared secret, injected from {@code internal.hmac.secret}
     * @param nonceStore Redis-backed nonce store for replay detection
     */
    public InternalAuthFilter(@Value("${internal.hmac.secret}") final String hmacSecret,
            final NonceStore nonceStore) {
        this.hmacSecret = hmacSecret;
        this.nonceStore = nonceStore;
    }

    /** Skips HMAC validation for actuator endpoints. */
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
            LOG.warn("[InternalAuthFilter] HMAC signature mismatch for user={}", sanitizeForLog(username));
            rejectRequest(response, "Invalid internal request signature");
            return;
        }

        if (!isTimestampFresh(timestamp)) {
            LOG.warn("[InternalAuthFilter] Stale or future-dated timestamp for user={}", sanitizeForLog(username));
            rejectRequest(response, "Stale or future-dated request signature");
            return;
        }

        if (!nonceStore.claim(nonce, NONCE_TTL_SECONDS)) {
            LOG.warn("[InternalAuthFilter] Replayed nonce detected for user={}", sanitizeForLog(username));
            rejectRequest(response, "Request signature already used");
            return;
        }

        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
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

    private boolean isTimestampFresh(final String timestamp) {
        try {
            final long requestMillis = Long.parseLong(timestamp);
            final long driftMillis = Math.abs(System.currentTimeMillis() - requestMillis);
            return driftMillis <= REPLAY_WINDOW_SECONDS * 1000;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

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

    private static String sanitizeForLog(final String value) {
        return value.replaceAll("[\r\n]", "_");
    }

    private void rejectRequest(final HttpServletResponse response, final String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
