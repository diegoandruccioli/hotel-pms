package com.hotelpms.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Gateway global filter that injects a trusted {@code X-Client-IP} header carrying
 * the real client IP, stripping any client-supplied value of the same header first.
 * {@link com.hotelpms.gateway.config.RateLimiterConfig} reads this same header
 * rather than re-deriving the client IP itself, so there is a single place that
 * decides how the IP is resolved.
 *
 * <p>Closes Finding #4 (security-report.md): the auth-service login lockout was
 * keyed solely on username, letting an unauthenticated attacker permanently lock
 * a known account (e.g. {@code admin}) for its legitimate owner. Binding the
 * lockout to (username, client IP) requires a non-spoofable client identifier.
 *
 * <p><b>GAP-17 (THREAT_MODEL.md)</b>: the original version of this filter always
 * used the TCP-level remote address. That is correct only when the gateway is
 * reachable exclusively through nginx ({@code frontend/nginx.conf} sets
 * {@code X-Real-IP} to its own {@code $remote_addr}, overwritten not appended) —
 * otherwise the TCP peer seen by the gateway for every request that DID go
 * through nginx is nginx's own container IP, identical for every real user, which
 * collapses the (username, IP) lockout key back down to (username) alone. When
 * {@code gateway.security.trust-forwarded-header} is {@code true} (set only in
 * {@code api-gateway-prod.yml}, where {@code docker-compose.prod.yml} no longer
 * publishes {@code api-gateway:8080} to the host, so nginx is the only path in),
 * this filter trusts nginx's {@code X-Real-IP} instead. When {@code false} (dev
 * default — the gateway port stays published in {@code docker-compose.yml}, so a
 * client can still skip nginx and forge the header), it falls back to the TCP
 * remote address exactly as before, never trusting a client-suppliable header.
 *
 * <p>Unlike {@link AuthenticationFilter}, which only runs on authenticated routes,
 * this filter is registered as a {@link GlobalFilter} and therefore also covers
 * pre-authentication routes such as {@code /api/v1/auth/login}.
 */
@Component
public class ClientIpFilter implements GlobalFilter, Ordered {

    /** Name of the gateway-injected header carrying the trusted client IP. */
    public static final String CLIENT_IP_HEADER = "X-Client-IP";

    /** Header nginx sets to its own {@code $remote_addr} (frontend/nginx.conf). Never appended. */
    private static final String NGINX_REAL_IP_HEADER = "X-Real-IP";

    private final boolean trustForwardedHeader;

    public ClientIpFilter(@Value("${gateway.security.trust-forwarded-header:false}") final boolean trustForwardedHeader) {
        this.trustForwardedHeader = trustForwardedHeader;
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        final String forwardedIp = trustForwardedHeader
                ? exchange.getRequest().getHeaders().getFirst(NGINX_REAL_IP_HEADER)
                : null;
        final String clientIp = forwardedIp != null && !forwardedIp.isBlank()
                ? forwardedIp
                : Objects.requireNonNull(
                        exchange.getRequest().getRemoteAddress(),
                        "Remote address must not be null").getAddress().getHostAddress();

        final ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(CLIENT_IP_HEADER))
                .header(CLIENT_IP_HEADER, clientIp)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Returns {@code HIGHEST_PRECEDENCE + 3}, placing this filter immediately
     * after {@link CsrfFilter} ({@code HIGHEST_PRECEDENCE + 2}) and before routing.
     *
     * @return the filter priority value
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }
}
