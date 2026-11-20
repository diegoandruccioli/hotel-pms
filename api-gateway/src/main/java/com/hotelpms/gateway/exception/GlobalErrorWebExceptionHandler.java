package com.hotelpms.gateway.exception;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
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
    private static final int MAX_CAUSE_DEPTH = 10;

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(final ServerWebExchange exchange, final Throwable ex) {
        final HttpStatus status = resolveStatus(ex);
        final boolean clientError = status.is4xxClientError();

        // Finding #17 pattern (security-report.md, LOW), same as
        // AbstractProblemDetailAdvice#handleFeignException: the exception
        // message here typically names the internal Docker service host
        // (e.g. "Failed to resolve 'fb-service'") — log it server-side only.
        if (clientError) {
            // A wrong path or method is the caller's mistake, not an outage:
            // no WARN noise for bots and scanners.
            LOG.debug("Gateway client error {} for {} {}: {}", status.value(),
                    exchange.getRequest().getMethod(), exchange.getRequest().getPath(), ex.toString());
        } else {
            LOG.warn("Gateway routing failure for {} {}: {}",
                    exchange.getRequest().getMethod(), exchange.getRequest().getPath(), ex.toString());
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", ERRORS_BASE_URI + slug(status));
        body.put("title", title(status));
        body.put("status", status.value());
        body.put("detail", detail(status));
        body.put("timestamp", Instant.now().toString());

        exchange.getResponse().setStatusCode(status);
        if (clientError && ex instanceof ResponseStatusException statusEx) {
            // e.g. Allow on a 405, Accept on a 415 (RFC 9110)
            exchange.getResponse().getHeaders().putAll(statusEx.getHeaders());
        }
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
     * Picks the response status. The gateway's own client errors (unknown path 404,
     * wrong method 405, malformed input 400, ...) keep their 4xx status: they are
     * not downstream failures. The cause chain (WebClient/Reactor-Netty wrap the
     * real network exception one or two levels deep) is searched for a timeout
     * (504) or a connection failure (502). Spring Cloud Gateway reports its own
     * route timeout as a 504 {@link ResponseStatusException}, honoured too. Anything
     * else falls back to 502 rather than leaking a raw 500.
     *
     * @param ex the exception the WebFlux filter chain surfaced
     * @return the HTTP status to respond with
     */
    private static HttpStatus resolveStatus(final Throwable ex) {
        Throwable cause = ex;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof TimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (cause instanceof UnknownHostException || cause instanceof ConnectException) {
                return HttpStatus.BAD_GATEWAY;
            }
            cause = cause.getCause();
        }
        if (ex instanceof ResponseStatusException statusEx) {
            final HttpStatus own = HttpStatus.resolve(statusEx.getStatusCode().value());
            if (own != null && (own.is4xxClientError() || own == HttpStatus.GATEWAY_TIMEOUT)) {
                return own;
            }
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private static String detail(final HttpStatus status) {
        return switch (status) {
            case BAD_GATEWAY -> "EXTERNAL_SERVICE_ERROR";
            default -> status.name();
        };
    }

    private static String slug(final HttpStatus status) {
        return switch (status) {
            case BAD_GATEWAY -> "external-service-error";
            default -> status.name().toLowerCase(Locale.ROOT).replace('_', '-');
        };
    }

    private static String title(final HttpStatus status) {
        return switch (status) {
            case BAD_GATEWAY -> "External Service Error";
            default -> status.getReasonPhrase();
        };
    }
}
