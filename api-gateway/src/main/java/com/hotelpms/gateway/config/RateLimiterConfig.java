package com.hotelpms.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Configures rate-limiting key resolver beans for the Spring Cloud Gateway.
 *
 * <p>Two resolvers are provided:
 * <ul>
 *   <li>{@code remoteAddrKeyResolver} — for pre-authentication routes (e.g. /auth/**).
 *       Always uses the TCP-level remote address, never {@code X-Forwarded-For}
 *       (client-forgeable — see Finding #3, security-report.md: {@code api-gateway:8080}
 *       is published directly to the host in {@code docker-compose.prod.yml}, so a
 *       client can skip nginx entirely and set that header to any value it wants).</li>
 *   <li>{@code userKeyResolver} — for authenticated routes. Uses the {@code X-Auth-User}
 *       header injected by {@link com.hotelpms.gateway.filter.AuthenticationFilter} after
 *       JWT validation. Per-user buckets prevent a single compromised or malicious account
 *       from flooding the API and causing a denial-of-service for other tenants. Falls back
 *       to the TCP remote address (never the forgeable header) when absent.</li>
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
     * <p>Always uses the TCP-level remote address. {@code X-Forwarded-For} is never
     * trusted: the gateway is directly reachable from outside the host
     * ({@code docker-compose.prod.yml}), so the header is attacker-controllable
     * whether or not nginx is in the path.
     *
     * <p>Marked {@code @Primary} so that {@code RequestRateLimiterGatewayFilterFactory}
     * can auto-wire a single default resolver without ambiguity. Routes that need
     * per-user buckets reference {@code userKeyResolver} explicitly via SpEL.
     *
     * @return a proxy-aware {@link KeyResolver} backed by client IP
     */
    @Bean
    @Primary
    public KeyResolver remoteAddrKeyResolver() {
        return exchange -> Mono.just(
                Objects.requireNonNull(
                        exchange.getRequest().getRemoteAddress(),
                        "Remote address must not be null").getAddress().getHostAddress());
    }

    /**
     * Resolves the rate-limit bucket key for authenticated routes.
     *
     * <p>The {@code X-Auth-User} header is injected by
     * {@link com.hotelpms.gateway.filter.AuthenticationFilter} after successful JWT
     * validation, so this resolver must run after that filter in the route filter chain.
     * When the header is present, each authenticated user receives an independent token
     * bucket (prefixed {@code "user:"}).  When absent, the resolver falls back to the
     * TCP remote address (prefixed {@code "ip:"}) — never {@code X-Forwarded-For}.
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
            return Mono.just("ip:" + Objects.requireNonNull(
                    exchange.getRequest().getRemoteAddress(),
                    "Remote address must not be null").getAddress().getHostAddress());
        };
    }
}
