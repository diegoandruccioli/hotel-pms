package com.hotelpms.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Configures rate-limiting key resolver beans for the Spring Cloud Gateway.
 *
 * <p>Two resolvers are provided:
 * <ul>
 *   <li>{@code remoteAddrKeyResolver} — for pre-authentication routes (e.g. /auth/**).
 *       Extracts the leftmost IP from {@code X-Forwarded-For} when the gateway sits
 *       behind a reverse proxy or load-balancer, falling back to the TCP remote address.
 *       Without this fix, all clients share a single rate-limit bucket equal to the
 *       proxy's IP, making per-IP isolation completely ineffective.</li>
 *   <li>{@code userKeyResolver} — for authenticated routes. Uses the {@code X-Auth-User}
 *       header injected by {@link com.hotelpms.gateway.filter.AuthenticationFilter} after
 *       JWT validation. Per-user buckets prevent a single compromised or malicious account
 *       from flooding the API and causing a denial-of-service for other tenants. Falls back
 *       to the proxy-aware IP when the header is absent.</li>
 * </ul>
 *
 * <p>Both beans are referenced by name in the {@code api-gateway.yml} rate-limiter
 * filter definitions:
 * <pre>
 *   key-resolver: "#{@remoteAddrKeyResolver}"   # pre-auth routes
 *   key-resolver: "#{@userKeyResolver}"          # authenticated routes
 * </pre>
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the rate-limit bucket key from the client IP address.
     *
     * <p>When an {@code X-Forwarded-For} header is present (set by a reverse proxy),
     * the leftmost — i.e. the original client — IP is used.  Without the header the
     * TCP-level remote address is used directly.
     *
     * @return a proxy-aware {@link KeyResolver} backed by client IP
     */
    @Bean
    public KeyResolver remoteAddrKeyResolver() {
        return exchange -> {
            final String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return Mono.just(forwarded.split(",")[0].trim());
            }
            return Mono.just(
                    Objects.requireNonNull(
                            exchange.getRequest().getRemoteAddress(),
                            "Remote address must not be null").getAddress().getHostAddress());
        };
    }

    /**
     * Resolves the rate-limit bucket key for authenticated routes.
     *
     * <p>The {@code X-Auth-User} header is injected by
     * {@link com.hotelpms.gateway.filter.AuthenticationFilter} after successful JWT
     * validation, so this resolver must run after that filter in the route filter chain.
     * When the header is present, each authenticated user receives an independent token
     * bucket (prefixed {@code "user:"}).  When absent, the resolver falls back to the
     * proxy-aware IP (prefixed {@code "ip:"}).
     *
     * @return a {@link KeyResolver} that keys by authenticated username, or by IP as fallback
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            final String user = exchange.getRequest().getHeaders().getFirst("X-Auth-User");
            if (user != null && !user.isBlank()) {
                return Mono.just("user:" + user);
            }
            final String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return Mono.just("ip:" + forwarded.split(",")[0].trim());
            }
            return Mono.just("ip:" + Objects.requireNonNull(
                    exchange.getRequest().getRemoteAddress(),
                    "Remote address must not be null").getAddress().getHostAddress());
        };
    }
}
