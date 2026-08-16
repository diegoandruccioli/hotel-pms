package com.hotelpms.gateway.config;

import com.hotelpms.gateway.filter.ClientIpFilter;
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
 *   <li>{@code remoteAddrKeyResolver} — for pre-authentication routes (e.g. /auth/**).</li>
 *   <li>{@code userKeyResolver} — for authenticated routes. Uses the {@code X-Auth-User}
 *       header injected by {@link com.hotelpms.gateway.filter.AuthenticationFilter} after
 *       JWT validation. Per-user buckets prevent a single compromised or malicious account
 *       from flooding the API and causing a denial-of-service for other tenants.</li>
 * </ul>
 *
 * <p>Both resolvers key their IP-based fallback on the {@link ClientIpFilter#CLIENT_IP_HEADER}
 * header rather than re-deriving the client IP independently (see {@link ClientIpFilter}'s
 * javadoc for the GAP-17 trust logic). {@code ClientIpFilter} runs as a {@code GlobalFilter}
 * at {@code Ordered.HIGHEST_PRECEDENCE + 3}, far earlier than any route-level
 * {@code RequestRateLimiter} filter, so the header is always already set by the time
 * these resolvers run — for every route, pre-auth included. Deriving the IP in exactly
 * one place, instead of duplicating the logic here, is what caused T-GW-10: this class
 * used to compute the TCP remote address on its own and was never updated alongside
 * {@code ClientIpFilter}.
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
     * Resolves the rate-limit bucket key from the client IP address, as resolved by
     * {@link ClientIpFilter}.
     *
     * <p>Marked {@code @Primary} so that {@code RequestRateLimiterGatewayFilterFactory}
     * can auto-wire a single default resolver without ambiguity. Routes that need
     * per-user buckets reference {@code userKeyResolver} explicitly via SpEL.
     *
     * @return a {@link KeyResolver} backed by the gateway-trusted client IP
     */
    @Bean
    @Primary
    public KeyResolver remoteAddrKeyResolver() {
        return exchange -> Mono.just(resolveClientIp(exchange.getRequest()));
    }

    /**
     * Resolves the rate-limit bucket key for authenticated routes.
     *
     * <p>The {@code X-Auth-User} header is injected by
     * {@link com.hotelpms.gateway.filter.AuthenticationFilter} after successful JWT
     * validation, so this resolver must run after that filter in the route filter chain.
     * When the header is present, each authenticated user receives an independent token
     * bucket (prefixed {@code "user:"}). When absent, the resolver falls back to the
     * gateway-trusted client IP (prefixed {@code "ip:"}), same as {@link #remoteAddrKeyResolver()}.
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
            return Mono.just("ip:" + resolveClientIp(exchange.getRequest()));
        };
    }

    private static String resolveClientIp(final org.springframework.http.server.reactive.ServerHttpRequest request) {
        final String clientIp = request.getHeaders().getFirst(ClientIpFilter.CLIENT_IP_HEADER);
        return clientIp != null && !clientIp.isBlank()
                ? clientIp
                : Objects.requireNonNull(
                        request.getRemoteAddress(),
                        "Remote address must not be null").getAddress().getHostAddress();
    }
}
