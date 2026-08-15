package com.hotelpms.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Gateway global filter that injects a trusted {@code X-Client-IP} header derived
 * from the TCP-level remote address, stripping any client-supplied value of the
 * same header first.
 *
 * <p>Closes Finding #4 (security-report.md): the auth-service login lockout was
 * keyed solely on username, letting an unauthenticated attacker permanently lock
 * a known account (e.g. {@code admin}) for its legitimate owner. Binding the
 * lockout to (username, client IP) requires a non-spoofable client identifier —
 * {@code auth-service} is never reachable directly from outside the host
 * ({@code docker-compose.prod.yml} exposes only {@code frontend:80} and
 * {@code api-gateway:8080}), so a header that only this filter can set is a
 * trustworthy channel for it.
 *
 * <p>Unlike {@link AuthenticationFilter}, which only runs on authenticated routes,
 * this filter is registered as a {@link GlobalFilter} and therefore also covers
 * pre-authentication routes such as {@code /api/v1/auth/login}.
 */
@Component
public class ClientIpFilter implements GlobalFilter, Ordered {

    /** Name of the gateway-injected header carrying the trusted client IP. */
    public static final String CLIENT_IP_HEADER = "X-Client-IP";

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        final String clientIp = Objects.requireNonNull(
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
