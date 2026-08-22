package com.hotelpms.gateway.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Verifies internal HMAC-signed requests to {@code /internal/**} — currently
 * only {@code POST /internal/events/notify} (T-GW-09b).
 *
 * <p>This is the gateway's first inbound trust boundary of this kind: until
 * now the gateway only ever produced signed internal headers for downstream
 * services (see {@code AuthenticationFilter}); it never received and
 * verified one itself. {@link com.hotelpms.internalauth.security.InternalAuthFilter}
 * — the servlet {@code OncePerRequestFilter} every other service reuses for
 * this — cannot run on this reactive/Netty gateway, so this filter
 * reimplements the same verification algorithm (identical header names,
 * identical {@code HMAC-SHA256(secret, "user:role:hotelId:timestamp:nonce")}
 * payload, identical ±60s replay window) against a reactive nonce store,
 * since the gateway only has {@code spring-boot-starter-data-redis-reactive}
 * on its classpath, not the blocking client {@code RedisNonceStore} needs.
 * Deliberately not shared code: this is the first reactive corner of the
 * backend, and forcing a servlet/reactive-spanning abstraction here would be
 * premature.
 */
@Component
public class InternalEventsAuthFilter implements WebFilter {

    private static final Logger LOG = LoggerFactory.getLogger(InternalEventsAuthFilter.class);

    private static final String INTERNAL_PATH_PREFIX = "/internal";

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HEADER_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HEADER_NONCE = "X-Auth-Nonce";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Maximum allowed drift between the signed timestamp and server time (T-GW-08). */
    private static final long REPLAY_WINDOW_SECONDS = 60;
    /** Nonce claims are remembered for twice the replay window, as a safety margin. */
    private static final long NONCE_TTL_SECONDS = REPLAY_WINDOW_SECONDS * 2;
    private static final String NONCE_KEY_PREFIX = "internal-auth:nonce:";

    private final String hmacSecret;
    private final ReactiveStringRedisTemplate redisTemplate;

    public InternalEventsAuthFilter(@Value("${internal.hmac.secret}") final String hmacSecret,
            final ReactiveStringRedisTemplate redisTemplate) {
        this.hmacSecret = hmacSecret;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        final String path = exchange.getRequest().getPath().value();
        if (!isGuardedPath(path)) {
            return chain.filter(exchange);
        }

        final HttpHeaders headers = exchange.getRequest().getHeaders();
        final String username = headers.getFirst(HEADER_USER);
        final String role = headers.getFirst(HEADER_ROLE);
        final String hotelId = headers.getFirst(HEADER_HOTEL);
        final String signature = headers.getFirst(HEADER_SIGNATURE);
        final String timestamp = headers.getFirst(HEADER_TIMESTAMP);
        final String nonce = headers.getFirst(HEADER_NONCE);

        if (isBlank(username) || isBlank(role) || isBlank(hotelId)
                || isBlank(signature) || isBlank(timestamp) || isBlank(nonce)) {
            return reject(exchange, "Missing required gateway authentication headers");
        }

        if (!isSignatureValid(username, role, hotelId, timestamp, nonce, signature)) {
            LOG.warn("[InternalEventsAuthFilter] HMAC signature mismatch for user={}", sanitizeForLog(username));
            return reject(exchange, "Invalid internal request signature");
        }

        if (!isTimestampFresh(timestamp)) {
            LOG.warn("[InternalEventsAuthFilter] Stale or future-dated timestamp for user={}",
                    sanitizeForLog(username));
            return reject(exchange, "Stale or future-dated request signature");
        }

        return claimNonce(nonce).flatMap(claimed -> {
            if (!claimed) {
                LOG.warn("[InternalEventsAuthFilter] Replayed nonce detected for user={}", sanitizeForLog(username));
                return reject(exchange, "Request signature already used");
            }
            exchange.getAttributes().put(InternalEventsAuthFilter.class.getName() + ".hotelId", hotelId);
            return chain.filter(exchange);
        });
    }

    /** Matches on a path-segment boundary — {@code /internal} or {@code /internal/*}, not {@code /internal-evil}. */
    private static boolean isGuardedPath(final String path) {
        return path.equals(INTERNAL_PATH_PREFIX) || path.startsWith(INTERNAL_PATH_PREFIX + "/");
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private boolean isSignatureValid(final String username, final String role, final String hotelId,
            final String timestamp, final String nonce, final String signature) {
        try {
            final String expected = computeHmac(username, role, hotelId, timestamp, nonce);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (final IllegalStateException e) {
            LOG.error("[InternalEventsAuthFilter] Failed to compute HMAC for signature verification", e);
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

    /** {@code SETNX} semantics via {@code setIfAbsent}: true only the first time a nonce is claimed. */
    private Mono<Boolean> claimNonce(final String nonce) {
        return redisTemplate.opsForValue()
                .setIfAbsent(NONCE_KEY_PREFIX + nonce, "1", Duration.ofSeconds(NONCE_TTL_SECONDS));
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

    private Mono<Void> reject(final ServerWebExchange exchange, final String message) {
        final ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        final byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
