package com.hotelpms.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
 * Validates the HMAC-signed internal routing headers injected by the API Gateway.
 *
 * <p>Applied only on paths requiring gateway authentication (e.g. user management
 * at {@code /api/v1/auth/users/**}). Public auth endpoints ({@code /login},
 * {@code /register}, etc.) and {@code /actuator} are excluded via
 * {@link #excludedPathPrefixes} — the same list {@code SecurityConfig} uses for
 * {@code permitAll()}, so the two can no longer drift apart (previously this
 * filter hardcoded its own inverted allowlist, independent of the
 * {@code SecurityConfig} permit list — AUDIT_ANALISI_2026-07.md item 2).
 *
 * <p>Reconciled onto the same contract as the other 5 services' identical
 * filter (fb/billing/frontdesk/guest/notification-service), ahead of the
 * {@code internal-auth-lib} extraction: presence check now rejects blank
 * headers, not just absent ones; rejection responses are a JSON body instead
 * of {@code sendError} + a string code; rejection logging now sanitizes the
 * username against CWE-117 log injection.
 *
 * <p>Anti-replay (T-GW-08): the signed payload includes a timestamp and a
 * random nonce, both validated against a tolerance window and a
 * {@link NonceStore}, so a captured header set cannot be replayed
 * indefinitely.
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
    private final List<String> excludedPathPrefixes;

    /**
     * Constructs the filter with the shared HMAC secret, the nonce store used
     * for replay detection, and the path prefixes exempt from HMAC validation.
     *
     * @param hmacSecret            the shared secret, injected from
     *                              {@code internal.hmac.secret}; must match
     *                              exactly the value configured in the API Gateway
     * @param nonceStore            the store used to detect re-used nonces (T-GW-08)
     * @param excludedPathPrefixes  request URI prefixes that bypass HMAC
     *                              validation entirely (e.g. {@code /actuator},
     *                              public unauthenticated endpoints)
     */
    public InternalAuthFilter(final String hmacSecret, final NonceStore nonceStore,
            final List<String> excludedPathPrefixes) {
        this.hmacSecret = hmacSecret;
        this.nonceStore = nonceStore;
        this.excludedPathPrefixes = List.copyOf(excludedPathPrefixes);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull final HttpServletRequest request) {
        final String path = request.getRequestURI();
        return excludedPathPrefixes.stream().anyMatch(path::startsWith);
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
