package com.hotelpms.gateway.filter;

import com.hotelpms.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
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
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

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

            final String signature = computeHmac(username, role);

            // Inline della variabile modifiedRequest per evitare declaration + immediate
            // return (code smell)
            return chain.filter(exchange.mutate()
                    .request(request.mutate()
                            .header(HEADER_USER, username)
                            .header(HEADER_ROLE, role)
                            .header(HEADER_SIGNATURE, signature)
                            .build())
                    .build());
        };
    }

    /**
     * Computes {@code HMAC-SHA256(secret, "username:role")} and returns the result
     * as a lowercase hex string.
     *
     * @param username the authenticated username extracted from the JWT
     * @param role     the role extracted from the JWT
     * @return hex-encoded HMAC digest
     * @throws IllegalStateException if the JVM does not support HmacSHA256 (should
     *                               never happen)
     */
    private String computeHmac(final String username, final String role) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            final SecretKeySpec keySpec = new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            final byte[] digest = mac.doFinal((username + ":" + role).getBytes(StandardCharsets.UTF_8));
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
