package com.hotelpms.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import lombok.SneakyThrows;

/**
 * Core Spring Security configuration for the authentication service.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String AUTH_ENDPOINTS = "/api/v1/auth/**";

    /** BCrypt cost factor — OWASP minimum 10, recommended 12 for modern hardware (T-AUTH-03). */
    private static final int BCRYPT_STRENGTH = 12;

    /**
     * Configures the main Spring Security filter chain.
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
                        .requestMatchers(AUTH_ENDPOINTS, "/actuator/**").permitAll()
                        .anyRequest().authenticated());

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
