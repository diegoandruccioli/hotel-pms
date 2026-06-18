package com.hotelpms.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * at {@code /api/v1/auth/users/**}). Unauthenticated public auth endpoints
 * ({@code /login}, {@code /register}, etc.) are excluded via the filter's
 * {@code shouldNotFilter} override.
 *
 * <p>Anti-replay (T-GW-08): the signed payload includes a timestamp and a
 * random nonce, both validated against a tolerance window and a
 * {@link NonceStore}, so a captured header set cannot be replayed
 * indefinitely.
 */
@RequiredArgsConstructor
public final class InternalAuthFilter extends OncePerRequestFilter {

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

    @Override
    protected boolean shouldNotFilter(@NonNull final HttpServletRequest request) {
        final String path = request.getRequestURI();
        return !path.startsWith("/api/v1/auth/users");
    }

    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain chain) throws ServletException, IOException {

        final String username = request.getHeader(HEADER_USER);
        final String role = request.getHeader(HEADER_ROLE);
        final String hotelId = request.getHeader(HEADER_HOTEL);
        final String signature = request.getHeader(HEADER_SIGNATURE);
        final String timestamp = request.getHeader(HEADER_TIMESTAMP);
        final String nonce = request.getHeader(HEADER_NONCE);

        if (username == null || role == null || hotelId == null || signature == null
                || timestamp == null || nonce == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MISSING_INTERNAL_HEADERS");
            return;
        }

        final String expected = computeHmac(username, role, hotelId, timestamp, nonce);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_INTERNAL_SIGNATURE");
            return;
        }

        if (!isTimestampFresh(timestamp)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "STALE_INTERNAL_SIGNATURE");
            return;
        }

        if (!nonceStore.claim(nonce, NONCE_TTL_SECONDS)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "REPLAYED_INTERNAL_SIGNATURE");
            return;
        }

        final var auth = new UsernamePasswordAuthenticationToken(
                username, "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(hotelId);
        SecurityContextHolder.getContext().setAuthentication(auth);
        final String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
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
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal((username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC_FAILED", ex);
        }
    }
}
