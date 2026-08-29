package com.hotelpms.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Global WebFlux error handler for the gateway's own routing layer.
 *
 * <p>Every internal service translates downstream-unavailability into a
 * consistent RFC 7807 Problem Details response — see {@code
 * AbstractProblemDetailAdvice#handleFeignException} (common-web-lib). That
 * mechanism is Spring MVC ({@code @RestControllerAdvice}) and does not apply
 * here: the gateway's proxy routes run entirely on the reactive WebFlux
 * filter chain, never through a {@code @RestController}, so a fully
 * unreachable downstream (container stopped, DNS resolution failing) fell
 * through to Spring Boot's default reactive error handler instead — a bare
 * {@code {"timestamp":...,"path":...,"status":500,"error":"Internal Server
 * Error"}} body with no {@code detail} code, breaking the error contract the
 * frontend's Axios interceptor relies on ({@code api.ts} only translates a
 * {@code detail} matching {@code ^[A-Z_]+$}) and leaking a raw 500 for what
 * is really "downstream unreachable", not a gateway bug.
 *
 * <p>{@code @Order(-2)} — one step ahead of Spring Boot's own {@code
 * DefaultErrorWebExceptionHandler} ({@code @Order(-1)}), so this runs first.
 */
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);
    private static final String ERRORS_BASE_URI = "https://hotel-pms.com/errors/";

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(final ServerWebExchange exchange, final Throwable ex) {
        final HttpStatus status = resolveStatus(ex);
        final String detail = status == HttpStatus.GATEWAY_TIMEOUT ? "GATEWAY_TIMEOUT" : "EXTERNAL_SERVICE_ERROR";
        final String slug = status == HttpStatus.GATEWAY_TIMEOUT ? "gateway-timeout" : "external-service-error";

        // Finding #17 pattern (security-report.md, LOW), same as
        // AbstractProblemDetailAdvice#handleFeignException: the exception
        // message here typically names the internal Docker service host
        // (e.g. "Failed to resolve 'fb-service'") — log it server-side only.
        LOG.warn("Gateway routing failure for {} {}: {}",
                exchange.getRequest().getMethod(), exchange.getRequest().getPath(), ex.toString());

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", ERRORS_BASE_URI + slug);
        body.put("title", status == HttpStatus.GATEWAY_TIMEOUT ? "Gateway Timeout" : "External Service Error");
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("timestamp", Instant.now().toString());

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        final byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (final Exception serializationEx) {
            LOG.error("Failed to serialize gateway error body", serializationEx);
            return exchange.getResponse().setComplete();
        }
        final DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Walks the cause chain (WebClient/Reactor-Netty wrap the real network
     * exception one or two levels deep) looking for a known connection or
     * timeout failure, and maps it to 502 or 504 respectively. Anything
     * unrecognized falls back to 502 rather than leaking a raw 500 — a
     * gateway routing failure is, by definition, "some downstream problem",
     * never a genuine application bug in the gateway itself.
     *
     * @param ex the exception the WebFlux filter chain surfaced
     * @return the HTTP status to respond with
     */
    private static HttpStatus resolveStatus(final Throwable ex) {
        Throwable cause = ex;
        for (int depth = 0; cause != null && depth < 10; depth++) {
            if (cause instanceof TimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (cause instanceof UnknownHostException || cause instanceof ConnectException) {
                return HttpStatus.BAD_GATEWAY;
            }
            cause = cause.getCause();
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
