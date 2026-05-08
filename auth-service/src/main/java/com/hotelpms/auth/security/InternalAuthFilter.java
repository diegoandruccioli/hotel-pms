package com.hotelpms.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
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
 */
@RequiredArgsConstructor
public final class InternalAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final String hmacSecret;

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String path = request.getRequestURI();
        return !path.startsWith("/api/v1/auth/users");
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain chain) throws ServletException, IOException {

        final String username = request.getHeader(HEADER_USER);
        final String role = request.getHeader(HEADER_ROLE);
        final String hotelId = request.getHeader(HEADER_HOTEL);
        final String signature = request.getHeader(HEADER_SIGNATURE);

        if (username == null || role == null || hotelId == null || signature == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MISSING_INTERNAL_HEADERS");
            return;
        }

        final String expected = computeHmac(username, role, hotelId);
        if (!expected.equals(signature)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_INTERNAL_SIGNATURE");
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

    private String computeHmac(final String username, final String role, final String hotelId) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal((username + ":" + role + ":" + hotelId).getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC_FAILED", ex);
        }
    }
}
