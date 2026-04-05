package com.hotelpms.reservation.security;

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
 * The filter enforces a two-layer check:
 * <ol>
 * <li><b>Presence check</b> – rejects requests that are missing any of the
 * three
 * required headers: {@code X-Auth-User}, {@code X-Auth-Role}, or
 * {@code X-Internal-Signature}.</li>
 * <li><b>HMAC-SHA256 integrity check</b> – recomputes
 * {@code HMAC-SHA256(sharedSecret, "username:role")} and compares it against
 * the received signature using a <em>constant-time</em> byte comparison
 * ({@link MessageDigest#isEqual}) to prevent timing-based side-channel attacks.
 * Requests with an absent or mismatched signature are rejected with
 * {@code 401 Unauthorized}.</li>
 * </ol>
 *
 * <p>
 * Only the API Gateway knows the shared secret and can produce a valid
 * signature,
 * making it impossible for any internal caller to spoof the {@code X-Auth-User}
 * /
 * {@code X-Auth-Role} headers without intercepting the secret.
 */
public final class InternalAuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(InternalAuthFilter.class);

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String hmacSecret;

    /**
     * Constructs the filter with the shared HMAC secret injected from
     * configuration.
     *
     * @param hmacSecret the shared secret, injected from
     *                   {@code internal.hmac.secret};
     *                   must match exactly the value configured in the API Gateway
     */
    public InternalAuthFilter(@Value("${internal.hmac.secret}") final String hmacSecret) {
        this.hmacSecret = hmacSecret;
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

        if (!StringUtils.hasText(username) || !StringUtils.hasText(role)
                || !StringUtils.hasText(hotelId) || !StringUtils.hasText(signature)) {
            rejectRequest(response, "Missing required gateway authentication headers");
            return;
        }

        if (!isSignatureValid(username, role, signature)) {
            LOG.warn("[InternalAuthFilter] HMAC signature mismatch for user={}", username);
            rejectRequest(response, "Invalid internal request signature");
            return;
        }

        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        // Store hotelId as details so the service layer can extract tenant context
        // without coupling the controller to the security mechanism.
        auth.setDetails(hotelId);

        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    /**
     * Verifies the received HMAC-SHA256 signature against a locally computed
     * digest.
     *
     * <p>
     * The comparison is performed with {@link MessageDigest#isEqual} which runs in
     * constant time regardless of how early the byte sequences differ, preventing
     * timing-based side-channel attacks.
     *
     * @param username  the username from the {@code X-Auth-User} header
     * @param role      the role from the {@code X-Auth-Role} header
     * @param signature the hex signature from the {@code X-Internal-Signature}
     *                  header
     * @return {@code true} if the recomputed digest matches the received signature
     */
    private boolean isSignatureValid(final String username, final String role, final String signature) {
        try {
            final String expected = computeHmac(username, role);
            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (final IllegalStateException e) {
            LOG.error("[InternalAuthFilter] Failed to compute HMAC for signature verification", e);
            return false;
        }
    }

    /**
     * Computes {@code HMAC-SHA256(secret, "username:role")} and returns the result
     * as a lowercase hex string, matching the gateway's signing convention.
     *
     * @param username the authenticated username
     * @param role     the role associated with the user
     * @return hex-encoded HMAC digest
     * @throws IllegalStateException if the JVM does not support HmacSHA256
     */
    private String computeHmac(final String username, final String role) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            final SecretKeySpec keySpec = new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            final byte[] digest = mac.doFinal((username + ":" + role).getBytes(StandardCharsets.UTF_8));
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
