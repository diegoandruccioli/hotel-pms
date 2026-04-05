package com.hotelpms.reservation.config;

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
 * (X-Auth-User, X-Auth-Role) from the current inbound HTTP request to all
 * outgoing Feign calls so that downstream services do not reject them with 401.
 */
@Configuration
public class FeignHeaderConfig {

    private static final String HEADER_USER = "X-Auth-User";
    private static final String HEADER_ROLE = "X-Auth-Role";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String HEADER_SIGNATURE = "X-Internal-Signature";
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
     * from the current request context and forwards them on every Feign call.
     * It also recomputes the internal HMAC signature to satisfy security filters.
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

            if (StringUtils.hasText(user) && StringUtils.hasText(role)) {
                template.header(HEADER_USER, user);
                template.header(HEADER_ROLE, role);
                template.header(HEADER_SIGNATURE, computeHmac(user, role));
            }
            if (StringUtils.hasText(hotel)) {
                template.header(HEADER_HOTEL, hotel);
            }
        };
    }

    /**
     * Computes the HMAC-SHA256 signature for the given username and role.
     *
     * @param username the authenticated username
     * @param role     the role associated with the user
     * @return hex-encoded HMAC digest
     */
    private String computeHmac(final String username, final String role) {
        try {
            final javax.crypto.Mac mac = javax.crypto.Mac.getInstance(HMAC_ALGORITHM);
            final javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    hmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            final byte[] digest = mac.doFinal((username + ":" + role).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (final java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("FEIGN_HMAC_SIGNATURE_FAILED", e);
        }
    }
}
