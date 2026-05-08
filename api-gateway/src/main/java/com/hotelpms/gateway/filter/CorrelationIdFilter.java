package com.hotelpms.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gateway global filter that propagates a correlation ID across the entire request chain.
 *
 * <p>If the incoming request carries an {@code X-Correlation-ID} header, its value is
 * forwarded unchanged to downstream services. If the header is absent, a new UUID is
 * generated and injected. The correlation ID is also added to the response so that
 * callers (e.g. the frontend) can include it in support tickets.
 *
 * <p>This filter runs before {@link AuthenticationFilter} (order
 * {@link Ordered#HIGHEST_PRECEDENCE}) so the correlation ID is available in all
 * subsequent filter log lines.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    /** Header name used throughout the platform for distributed tracing correlation. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        final String correlationId = resolveCorrelationId(exchange.getRequest());

        log.debug("[GATEWAY] correlationId={} | path={}", correlationId,
                exchange.getRequest().getPath().value());

        final ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doOnSuccess(v -> exchange.getResponse().getHeaders()
                        .set(CORRELATION_ID_HEADER, correlationId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String resolveCorrelationId(final ServerHttpRequest request) {
        final String existing = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return UUID.randomUUID().toString();
    }
}
