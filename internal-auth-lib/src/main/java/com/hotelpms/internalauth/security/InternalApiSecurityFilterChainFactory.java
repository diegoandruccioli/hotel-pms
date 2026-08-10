package com.hotelpms.internalauth.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/**
 * Builds the {@link SecurityFilterChain} shared by every internal service
 * (all HTTP APIs behind the API Gateway, reachable only via HMAC-signed
 * internal calls): stateless sessions, CSRF disabled (no browser sessions),
 * actuator endpoints open, everything else requiring {@link InternalAuthFilter}
 * to accept the request (T-GW-08).
 *
 * <p>Each service still declares its own {@code @Configuration} class that
 * calls this factory — see {@code SecurityConfig} in billing-service,
 * fb-service, frontdesk-service, guest-service, notification-service — so
 * that {@code @EnableWebSecurity}/{@code @EnableMethodSecurity} and the
 * {@link NonceStore} bean stay declared per-service, consistent with how
 * {@link InternalAuthFilter} is already wired today.
 */
public final class InternalApiSecurityFilterChainFactory {

    private InternalApiSecurityFilterChainFactory() {
    }

    /**
     * Builds the filter chain: stateless, CSRF-disabled, actuator open,
     * everything else gated by {@link InternalAuthFilter}.
     *
     * @param http             the HttpSecurity builder
     * @param hmacSecret       the internal HMAC secret shared with the API Gateway
     * @param nonceStore       the nonce store used for anti-replay checks (T-GW-08)
     * @param exemptPathPrefixes path prefixes {@link InternalAuthFilter} should not
     *                           enforce the HMAC signature on (typically actuator)
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @SuppressWarnings({"PMD.SignatureDeclareThrowsException", "null"})
    public static SecurityFilterChain build(final HttpSecurity http, final String hmacSecret,
            final NonceStore nonceStore, final List<String> exemptPathPrefixes) throws Exception {
        http
                // codeql[java/spring-disabled-csrf-protection]: no browser ever reaches this chain — it
                // gates only internal, gateway-fronted service-to-service calls, stateless (no session
                // cookie, the CSRF attack vector this rule protects against) and authenticated by an
                // HMAC signature (InternalAuthFilter) an attacker-controlled page cannot forge. This is
                // exactly Spring's own documented carve-out: "only for services used only by non-browser
                // clients."
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new InternalAuthFilter(hmacSecret, nonceStore, exemptPathPrefixes),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
