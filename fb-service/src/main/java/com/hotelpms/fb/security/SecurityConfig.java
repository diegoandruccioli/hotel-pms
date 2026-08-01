package com.hotelpms.fb.security;

import com.hotelpms.internalauth.security.InternalApiSecurityFilterChainFactory;
import com.hotelpms.internalauth.security.InternalAuthFilter;
import com.hotelpms.internalauth.security.NonceStore;
import com.hotelpms.internalauth.security.RedisNonceStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Security configuration for the Food &amp; Beverage Service. See
 * {@link InternalApiSecurityFilterChainFactory} for the shared filter chain
 * logic.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final List<String> HMAC_EXEMPT_PATH_PREFIXES = List.of("/actuator");

    private final String hmacSecret;

    /**
     * Constructs the security configuration with the shared HMAC secret.
     *
     * @param hmacSecret the internal HMAC secret, shared with the API Gateway;
     *                   injected from {@code internal.hmac.secret}
     */
    public SecurityConfig(@Value("${internal.hmac.secret}") final String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    /**
     * Provides the nonce store used by {@link InternalAuthFilter} to detect
     * replayed internal requests (T-GW-08).
     *
     * @param redisTemplate the shared Redis client, autoconfigured by
     *                      {@code spring-boot-starter-data-redis}
     * @return a Redis-backed {@link NonceStore}
     */
    @Bean
    public NonceStore nonceStore(final StringRedisTemplate redisTemplate) {
        return new RedisNonceStore(redisTemplate);
    }

    /**
     * Configures the security filter chain.
     *
     * @param http       the HttpSecurity builder
     * @param nonceStore the nonce store used for anti-replay checks (T-GW-08)
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @SuppressWarnings({"PMD.SignatureDeclareThrowsException", "null"})
    public SecurityFilterChain securityFilterChain(final HttpSecurity http, final NonceStore nonceStore)
            throws Exception {
        return InternalApiSecurityFilterChainFactory.build(http, hmacSecret, nonceStore, HMAC_EXEMPT_PATH_PREFIXES);
    }
}
