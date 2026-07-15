package com.hotelpms.notification.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for notification-service.
 *
 * <p>All requests except actuator endpoints require a valid HMAC internal signature
 * (T-GW-08). Sessions are stateless; CSRF is disabled (API-only, no browser sessions).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String hmacSecret;

    /**
     * Constructs the configuration with the shared HMAC secret.
     *
     * @param hmacSecret the internal HMAC secret, shared with all microservices
     */
    public SecurityConfig(@Value("${internal.hmac.secret}") final String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    /**
     * Provides the Redis-backed nonce store used by {@link InternalAuthFilter}.
     *
     * @param redisTemplate the shared Redis client
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
     * @param nonceStore the nonce store for anti-replay checks
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @SuppressWarnings({"PMD.SignatureDeclareThrowsException", "null"})
    public SecurityFilterChain securityFilterChain(final HttpSecurity http, final NonceStore nonceStore)
            throws Exception {
        http
                .csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new InternalAuthFilter(hmacSecret, nonceStore),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
