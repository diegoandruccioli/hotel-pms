package com.hotelpms.guest.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Guest Service.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
     * Configures the security filter chain.
     *
     * @param http the HttpSecurity builder
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        http
                .csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new InternalAuthFilter(hmacSecret), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
