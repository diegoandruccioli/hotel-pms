package com.hotelpms.config;

import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Spring Security configuration for the Config Server.
 *
 * <p>All requests to the main application port (:8888) require HTTP Basic
 * authentication. Sessions are stateless because config clients authenticate
 * on every request; CSRF protection is disabled as there is no browser
 * session or form-based interaction. The management port (:8090) runs on a
 * separate embedded server and is not affected by this filter chain.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain for the config server.
     *
     * @param http the {@link HttpSecurity} to configure
     * @return the built {@link SecurityFilterChain}
     */
    @Bean
    @SneakyThrows(Exception.class)
    public SecurityFilterChain filterChain(final HttpSecurity http) {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().authenticated())
            .httpBasic(withDefaults());
        return http.build();
    }
}
