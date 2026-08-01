package com.hotelpms.internalauth.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Feign {@link RequestInterceptor} that propagates gateway authentication
 * headers (X-Auth-User, X-Auth-Role, X-Auth-Hotel) from the current inbound
 * HTTP request to every outgoing Feign call, and recomputes the HMAC-SHA256
 * internal signature so that downstream service {@code InternalAuthFilter}
 * instances accept the call (T-GW-07 / T-GST-05).
 *
 * <p>Each outgoing call gets a freshly computed timestamp + nonce (T-GW-08):
 * the calling service acts as its own signer for calls it originates, rather
 * than forwarding the inbound gateway signature, which avoids reusing a nonce
 * that may already have been claimed when this service's own
 * {@code InternalAuthFilter} validated the inbound request.
 *
 * <p>When no inbound {@code ServletRequestAttributes} are bound to the
 * current thread — e.g. a scheduled batch job running outside an HTTP
 * request — the {@link FeignAuthFallbackProvider} supplied at construction is
 * consulted instead. Services with no such calls construct this class with
 * {@code Optional::empty}.
 */
public final class InternalFeignAuthInterceptor implements RequestInterceptor {

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HEADER_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HEADER_NONCE = "X-Auth-Nonce";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String hmacSecret;
    private final FeignAuthFallbackProvider fallbackProvider;

    /**
     * Constructs the interceptor with the shared HMAC secret and the fallback
     * used for calls made outside an HTTP request context.
     *
     * @param hmacSecret       the internal HMAC secret shared with all microservices
     * @param fallbackProvider resolves the auth context to use when no inbound
     *                         {@code ServletRequestAttributes} are bound to the
     *                         current thread; pass {@code Optional::empty} for
     *                         services with no such calls
     */
    public InternalFeignAuthInterceptor(final String hmacSecret, final FeignAuthFallbackProvider fallbackProvider) {
        this.hmacSecret = hmacSecret;
        this.fallbackProvider = fallbackProvider;
    }

    @Override
    public void apply(final RequestTemplate template) {
        final ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            signFromInboundRequest(template, attrs.getRequest());
        } else {
            fallbackProvider.resolve().ifPresent(ctx -> sign(template, ctx.user(), ctx.role(), ctx.hotelId()));
        }
    }

    private void signFromInboundRequest(final RequestTemplate template, final HttpServletRequest request) {
        final String user = request.getHeader(HEADER_USER);
        final String role = request.getHeader(HEADER_ROLE);
        final String hotel = request.getHeader(HEADER_HOTEL);
        if (StringUtils.hasText(user) && StringUtils.hasText(role) && StringUtils.hasText(hotel)) {
            sign(template, user, role, hotel);
        }
    }

    private void sign(final RequestTemplate template, final String user, final String role, final String hotel) {
        final String timestamp = String.valueOf(System.currentTimeMillis());
        final String nonce = UUID.randomUUID().toString();
        template.header(HEADER_USER, user);
        template.header(HEADER_ROLE, role);
        template.header(HEADER_HOTEL, hotel);
        template.header(HEADER_TIMESTAMP, timestamp);
        template.header(HEADER_NONCE, nonce);
        template.header(HEADER_SIGNATURE, computeHmac(user, role, hotel, timestamp, nonce));
    }

    /**
     * Computes {@code HMAC-SHA256(secret, "username:role:hotelId:timestamp:nonce")}
     * and returns the result as a lowercase hex string, matching the payload format
     * used by {@code InternalAuthFilter} in every downstream service (T-GW-08).
     *
     * @param username  the authenticated username
     * @param role      the role associated with the user
     * @param hotelId   the hotel UUID associated with the user
     * @param timestamp the epoch-millis timestamp generated for this call
     * @param nonce     the random nonce generated for this call
     * @return hex-encoded HMAC digest
     */
    private String computeHmac(final String username, final String role, final String hotelId,
            final String timestamp, final String nonce) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            final SecretKeySpec keySpec =
                    new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            final byte[] digest = mac.doFinal(
                    (username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("FEIGN_HMAC_SIGNATURE_FAILED", e);
        }
    }
}
