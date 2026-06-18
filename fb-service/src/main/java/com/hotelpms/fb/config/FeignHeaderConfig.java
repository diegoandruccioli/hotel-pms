package com.hotelpms.fb.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign configuration that propagates gateway authentication headers
 * (X-Auth-User, X-Auth-Role, X-Auth-Hotel) from the current inbound HTTP
 * request to all outgoing Feign calls and recomputes the HMAC-SHA256 signature
 * so that downstream InternalAuthFilter instances accept the request.
 *
 * <p>Each outgoing call gets a freshly computed timestamp + nonce (T-GW-08):
 * this service acts as its own signer for calls it originates, rather than
 * forwarding the inbound gateway signature, which avoids reusing a nonce
 * that may already have been claimed when this service's own InternalAuthFilter
 * validated the inbound request.
 */
@Configuration
public class FeignHeaderConfig {

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
    private static final String HEADER_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HEADER_NONCE = "X-Auth-Nonce";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String hmacSecret;

    /**
     * Constructs the Feign configuration with the shared HMAC secret.
     *
     * @param hmacSecret the internal HMAC secret, shared with all microservices
     */
    public FeignHeaderConfig(@Value("${internal.hmac.secret}") final String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    /**
     * Registers a RequestInterceptor that extracts the gateway auth headers
     * from the current request context, forwards them on every Feign call,
     * and recomputes the X-Internal-Signature so downstream services accept the call.
     *
     * @return the configured RequestInterceptor
     */
    @Bean
    public RequestInterceptor authHeaderInterceptor() {
        return (final RequestTemplate template) -> {
            final ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attrs == null) {
                return;
            }
            final HttpServletRequest request = attrs.getRequest();
            final String user = request.getHeader(HEADER_USER);
            final String role = request.getHeader(HEADER_ROLE);
            final String hotel = request.getHeader(HEADER_HOTEL);

            if (StringUtils.hasText(user) && StringUtils.hasText(role) && StringUtils.hasText(hotel)) {
                final String timestamp = String.valueOf(System.currentTimeMillis());
                final String nonce = java.util.UUID.randomUUID().toString();
                template.header(HEADER_USER, user);
                template.header(HEADER_ROLE, role);
                template.header(HEADER_HOTEL, hotel);
                template.header(HEADER_TIMESTAMP, timestamp);
                template.header(HEADER_NONCE, nonce);
                template.header(HEADER_SIGNATURE, computeHmac(user, role, hotel, timestamp, nonce));
            }
        };
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
            final javax.crypto.Mac mac = javax.crypto.Mac.getInstance(HMAC_ALGORITHM);
            final javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    hmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            final byte[] digest = mac.doFinal(
                    (username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (final java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("FEIGN_HMAC_SIGNATURE_FAILED", e);
        }
    }
}
