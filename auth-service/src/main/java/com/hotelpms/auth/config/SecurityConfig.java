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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    /** BCrypt cost factor — OWASP minimum 10, recommended 12 for modern hardware (T-AUTH-03). */
    private static final int BCRYPT_STRENGTH = 12;

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
     * Provides the password encoder bean.
     *
     * <p>Cost factor 12 satisfies the OWASP recommendation for BCrypt (minimum 10,
     * recommended 12 for modern hardware).  The value is intentionally higher than
     * the BCrypt default (10) to increase the work factor against brute-force
     * attacks on stolen hashes (T-AUTH-03).
     *
     * @return a {@link BCryptPasswordEncoder} with strength 12
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
