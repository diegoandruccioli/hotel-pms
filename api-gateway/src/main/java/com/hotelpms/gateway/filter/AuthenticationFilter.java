package com.hotelpms.gateway.filter;

import com.hotelpms.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Reactive Authentication Filter for validating JWTs and applying headers.
 *
 * <p>
 * After a successful JWT validation, this filter:
 * <ol>
 * <li>Extracts {@code username} and {@code role} from the token claims.</li>
 * <li>Injects {@code X-Auth-User} and {@code X-Auth-Role} headers so downstream
 * services can identify the caller without re-parsing the JWT.</li>
 * <li>Computes an {@code HMAC-SHA256} signature over {@code "username:role"}
 * using
 * the shared secret {@code internal.hmac.secret} and injects it as the
 * {@code X-Internal-Signature} header.</li>
 * </ol>
 *
 * <p>
 * Downstream {@code InternalAuthFilter} instances verify this signature to
 * guarantee that the gateway — and only the gateway — was the entry point,
 * making
 * it impossible for internal callers to spoof {@code X-Auth-User} /
 * {@code X-Auth-Role}.
 */
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // -----------------------------------------------------------------------
    // RBAC configuration
    // -----------------------------------------------------------------------

    /** Roles allowed to perform any authenticated operation. */
    private static final Set<String> OPERATIONAL_ROLES = Set.of("ADMIN", "OWNER", "RECEPTIONIST");

    /** Roles allowed to perform administrative write operations. */
    private static final Set<String> ADMIN_OWNER_ROLES = Set.of("ADMIN", "OWNER");

    /**
     * Path prefixes whose write operations (POST/PUT/PATCH/DELETE) are restricted to
     * ADMIN and OWNER. GET is permitted for RECEPTIONIST (e.g. room selection
     * during check-in).
     */
    private static final Set<String> WRITE_RESTRICTED_PREFIXES = Set.of(
            "/api/v1/room-types"
    );

    /**
     * Path prefixes fully restricted to ADMIN/OWNER for ALL HTTP methods.
     * RECEPTIONIST cannot access these paths even with GET.
     */
    private static final Set<String> FULLY_RESTRICTED_PREFIXES = Set.of(
            "/api/v1/reports"
    );

    /**
     * Full paths restricted to ADMIN/OWNER regardless of HTTP method (room
     * structural management; PUT on rooms replaces the full room definition).
     */
    private static final Set<HttpMethod> ROOM_ADMIN_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE
    );

    /** Path prefix for user management — always restricted to ADMIN/OWNER. */
    private static final String USERS_PATH_PREFIX = "/api/v1/auth/users";

    private final JwtUtil jwtUtil;
    private final String hmacSecret;

    /**
     * Constructs the filter with JWT utility and the internal HMAC shared secret.
     *
     * @param jwtUtil    the JWT utility for token parsing and validation
     * @param hmacSecret the shared secret used to sign internal routing headers;
     *                   injected from {@code internal.hmac.secret}
     */
    @Autowired
    public AuthenticationFilter(final JwtUtil jwtUtil,
            @Value("${internal.hmac.secret}") final String hmacSecret) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.hmacSecret = hmacSecret;
    }

    /**
     * Applies the filter logic to the gateway chain.
     *
     * @param config the configuration object
     * @return the GatewayFilter function
     */
    @Override
    public GatewayFilter apply(final Config config) {
        return (exchange, chain) -> {
            final ServerHttpRequest request = exchange.getRequest();

            final org.springframework.http.HttpCookie cookie = request.getCookies().getFirst("jwt");

            if (cookie == null) {
                return this.onError(exchange, "No JWT cookie found", HttpStatus.UNAUTHORIZED);
            }

            final String tokenValue = cookie.getValue();
            if (tokenValue == null || tokenValue.isEmpty()) {
                return this.onError(exchange, "No JWT cookie found", HttpStatus.UNAUTHORIZED);
            }

            final Claims claims = parseJwtSafely(tokenValue);
            if (claims == null) {
                return this.onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }

            final String username = claims.getSubject();
            final String role = claims.get("role", String.class);
            final String hotelId = claims.get("hotelId", String.class);

            if (hotelId == null || hotelId.isEmpty()) {
                return this.onError(exchange, "JWT missing hotelId claim", HttpStatus.UNAUTHORIZED);
            }

            // RBAC enforcement — runs before the request is forwarded
            if (!isAccessAllowed(role, request.getPath().value(), request.getMethod())) {
                return this.onError(exchange, "ACCESS_DENIED", HttpStatus.FORBIDDEN);
            }

            final String signature = computeHmac(username, role, hotelId);

            return chain.filter(exchange.mutate()
                    .request(request.mutate()
                            .headers(headers -> {
                                headers.remove(HEADER_USER);
                                headers.remove(HEADER_ROLE);
                                headers.remove(HEADER_HOTEL);
                                headers.remove(HEADER_SIGNATURE);
                            })
                            .header(HEADER_USER, username)
                            .header(HEADER_ROLE, role)
                            .header(HEADER_HOTEL, hotelId)
                            .header(HEADER_SIGNATURE, signature)
                            .build())
                    .build());
        };
    }

    /**
     * Computes {@code HMAC-SHA256(secret, "username:role:hotelId")} and returns
     * the result as a lowercase hex string.
     *
     * <p>Including {@code hotelId} in the signed payload ensures that downstream
     * services can verify the integrity of the tenant-isolation header
     * {@code X-Auth-Hotel} and not just the identity headers, closing a potential
     * header-tampering vector on the internal network.
     *
     * @param username the authenticated username extracted from the JWT
     * @param role     the role extracted from the JWT
     * @param hotelId  the hotel UUID extracted from the JWT {@code hotelId} claim
     * @return hex-encoded HMAC digest
     * @throws IllegalStateException if the JVM does not support HmacSHA256 (should
     *                               never happen)
     */
    private String computeHmac(final String username, final String role, final String hotelId) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            final SecretKeySpec keySpec = new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            final byte[] digest = mac.doFinal(
                    (username + ":" + role + ":" + hotelId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC_SIGNATURE_FAILED", e);
        }
    }

    /**
     * Safely parses the JWT token, returning null if it is invalid or expired.
     */
    private Claims parseJwtSafely(final String token) {
        try {
            return jwtUtil.getAllClaimsFromToken(token);
        } catch (final JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Enforces the RBAC policy for the given role, request path, and HTTP method.
     *
     * <p>Rules:
     * <ul>
     *   <li>GUEST — no access to any authenticated API.</li>
     *   <li>RECEPTIONIST — cannot perform write operations on room-types or financial
     *       reports, cannot create/delete rooms, cannot access user management.</li>
     *   <li>ADMIN / OWNER — full access.</li>
     * </ul>
     *
     * @param role   the authenticated user's role (from JWT)
     * @param path   the request URI path
     * @param method the HTTP method
     * @return {@code true} if the request is permitted, {@code false} otherwise
     */
    private static boolean isAccessAllowed(final String role, final String path, final HttpMethod method) {
        if (role == null || !OPERATIONAL_ROLES.contains(role)) {
            return false;
        }
        if (ADMIN_OWNER_ROLES.contains(role)) {
            return true;
        }
        // RECEPTIONIST checks
        if (path.startsWith(USERS_PATH_PREFIX)) {
            return false;
        }
        if (path.startsWith("/api/v1/rooms") && ROOM_ADMIN_METHODS.contains(method)) {
            return false;
        }
        for (final String prefix : FULLY_RESTRICTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        for (final String prefix : WRITE_RESTRICTED_PREFIXES) {
            if (path.startsWith(prefix) && method != HttpMethod.GET) {
                return false;
            }
        }
        return true;
    }

    private Mono<Void> onError(final ServerWebExchange exchange, final String err, final HttpStatus httpStatus) {
        final ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    /**
     * Configuration class for the AbstractGatewayFilterFactory.
     */
    public static class Config {
        // Put the configuration properties
    }
}
