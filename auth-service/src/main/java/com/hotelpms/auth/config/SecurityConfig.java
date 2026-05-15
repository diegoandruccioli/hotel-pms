package com.hotelpms.auth.config;

import com.hotelpms.auth.security.InternalAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import lombok.SneakyThrows;

/**
 * Core Spring Security configuration for the authentication service.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String AUTH_PUBLIC_ENDPOINTS = "/api/v1/auth/login";
    private static final String AUTH_REGISTER = "/api/v1/auth/register";
    private static final String AUTH_REFRESH = "/api/v1/auth/refresh";
    private static final String AUTH_LOGOUT = "/api/v1/auth/logout";
    private static final String AUTH_CHANGE_PW = "/api/v1/auth/change-password";
    private static final String AUTH_ME = "/api/v1/auth/me";

    // Argon2id parameters — exercise A04:2025, T-AUTH-03 (memory-hard KDF)
    private static final int ARGON2_SALT_LEN = 16; // bytes
    private static final int ARGON2_HASH_LEN = 32; // bytes
    private static final int ARGON2_PARALLELISM = 1;
    private static final int ARGON2_MEMORY = 19 * 1024; // 19 MiB in KiB
    private static final int ARGON2_ITERATIONS = 2;
    // BCrypt kept for lazy-rehash migration of pre-existing hashes
    private static final int BCRYPT_LEGACY_STRENGTH = 12;

    private final String hmacSecret;

    /**
     * Constructs the security config with the shared HMAC secret (for InternalAuthFilter).
     *
     * @param hmacSecret the internal shared secret from {@code internal.hmac.secret}
     */
    public SecurityConfig(@Value("${internal.hmac.secret}") final String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    /**
     * Configures the main Spring Security filter chain.
     * Public auth endpoints are permit-all; the /users management paths require
     * HMAC-verified gateway headers via InternalAuthFilter.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    @SneakyThrows(Exception.class)
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                AUTH_PUBLIC_ENDPOINTS, AUTH_REGISTER,
                                AUTH_REFRESH, AUTH_LOGOUT,
                                AUTH_CHANGE_PW, AUTH_ME,
                                "/actuator/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new InternalAuthFilter(hmacSecret),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Provides the password encoder bean (T-AUTH-03, A04:2025 — Cryptographic Failures).
     *
     * <p>New passwords are hashed with <b>Argon2id</b> (memory-hard KDF, resistant to
     * GPU/ASIC brute-force): 19 MiB memory, 2 iterations, parallelism 1 — parameters
     * matching the course exercise specification.</p>
     *
     * <p>A {@link DelegatingPasswordEncoder} wraps both Argon2id (default) and BCrypt
     * (legacy). Hashes stored without a {@code {prefix}} are treated as BCrypt.
     * The lazy-rehash mechanism in {@code AuthServiceImpl.login()} upgrades
     * every BCrypt hash to Argon2id on the owner's next successful login,
     * with zero downtime and no forced password resets.</p>
     *
     * @return a {@link DelegatingPasswordEncoder} defaulting to Argon2id
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        final Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2", new Argon2PasswordEncoder(
                ARGON2_SALT_LEN, ARGON2_HASH_LEN,
                ARGON2_PARALLELISM, ARGON2_MEMORY, ARGON2_ITERATIONS));
        encoders.put("bcrypt", new BCryptPasswordEncoder(BCRYPT_LEGACY_STRENGTH));
        return new DelegatingPasswordEncoder("argon2", encoders);
    }
}
