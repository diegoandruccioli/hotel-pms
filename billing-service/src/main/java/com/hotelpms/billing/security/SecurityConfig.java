package com.hotelpms.billing.security;

import com.hotelpms.internalauth.redis.RedisDnsResolution;
import com.hotelpms.internalauth.redis.RedisSocketOptions;
import com.hotelpms.internalauth.security.InternalApiSecurityFilterChainFactory;
import com.hotelpms.internalauth.security.InternalAuthFilter;
import com.hotelpms.internalauth.security.NonceStore;
import com.hotelpms.internalauth.security.RedisNonceStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Security configuration for the Billing Service. See
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
     * Makes the Redis client resolve the Redis host again on every reconnect, so a Redis that comes back on another
     * address is picked up instead of retried at the old one for minutes (see {@link RedisDnsResolution}).
     *
     * @return the customizer Spring Boot applies to the Lettuce client resources
     */
    @Bean
    public static ClientResourcesBuilderCustomizer redisDnsResolutionCustomizer() {
        return RedisDnsResolution::apply;
    }

    /**
     * Makes the Redis client notice a peer that vanished without closing the connection (keepalive and TCP user
     * timeout, see {@link RedisSocketOptions}). Spring Boot applies this after its own socket options and this
     * replaces them, so the configured connect timeout is passed along.
     *
     * @param properties the Spring Boot Redis properties, for the configured connect timeout; optional because
     *                   a web slice test loads this class without the Redis auto-configuration
     * @return the customizer Spring Boot applies to the Lettuce client options
     */
    @Bean
    public static LettuceClientOptionsBuilderCustomizer redisSocketOptionsCustomizer(
            final ObjectProvider<DataRedisProperties> properties) {
        return builder -> {
            final DataRedisProperties redis = properties.getIfAvailable();
            RedisSocketOptions.apply(builder, redis == null ? null : redis.getConnectTimeout());
        };
    }

    /**
     * Configures the security filter chain.
     *
     * @param http       the HttpSecurity builder
     * @param nonceStore the nonce store used for anti-replay checks (T-GW-08)
     * @return the configured SecurityFilterChain
     */
    @Bean
    @SuppressWarnings("null")
    public SecurityFilterChain securityFilterChain(final HttpSecurity http, final NonceStore nonceStore) {
        return InternalApiSecurityFilterChainFactory.build(http, hmacSecret, nonceStore, HMAC_EXEMPT_PATH_PREFIXES);
    }
}
