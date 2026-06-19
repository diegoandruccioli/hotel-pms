package com.hotelpms.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Global gateway filter that enforces CSRF protection for all state-mutating
 * HTTP requests (POST, PUT, PATCH, DELETE) using the
 * <b>Double Submit Cookie</b> pattern.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>On login / register / refresh the {@code auth-service} sets a
 *       non-httpOnly {@code csrf_token} cookie alongside the httpOnly JWT.
 *       The SPA JavaScript can read this cookie via {@code document.cookie}.</li>
 *   <li>For every mutating request the frontend reads the cookie value and echoes
 *       it in the {@code X-CSRF-Token} request header.</li>
 *   <li>This filter rejects the request with HTTP 403 if the header is absent,
 *       blank, or does not exactly match the cookie value.</li>
 * </ol>
 *
 * <h2>Why it is secure</h2>
 * A cross-site attacker can trigger the browser to send the JWT cookie
 * automatically (that is the very definition of CSRF), but the browser's
 * <em>same-origin policy</em> prevents the attacker's JavaScript from reading
 * the {@code csrf_token} cookie, making it impossible to forge the matching
 * header value.
 *
 * <h2>Excluded paths</h2>
 * {@code /api/v1/auth/login} and {@code /api/v1/auth/register} are excluded
 * because the CSRF cookie does not exist yet at that point (the session has not
 * been established). These endpoints are protected by other means (rate limiting,
 * account lockout — T-AUTH-02).
 *
 * <p>{@code /api/v1/auth/refresh} is excluded for the same reason login/register
 * are: it does not depend on this filter for protection. The refresh cookie is
 * httpOnly, path-scoped to {@code /api/v1/auth}, and — like every session cookie
 * in this app — {@code SameSite=Strict}, which already prevents the browser from
 * attaching it to a cross-site request before this filter ever runs. Requiring a
 * CSRF token here only re-introduces the problem this pattern exists to solve:
 * if the {@code csrf_token} cookie itself has expired or been lost, refresh — the
 * one endpoint that would re-issue it — becomes permanently unreachable, leaving
 * a legitimate session stuck with no client-side recovery path.
 *
 * <p>Implements threat <b>T-GW-05</b> from the project threat model (OWASP A01 —
 * Broken Access Control / Cross-Site Request Forgery).
 */
@Component
public class CsrfFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CsrfFilter.class);

    /** Name of the non-httpOnly cookie that carries the CSRF synchronization token. */
    static final String CSRF_COOKIE_NAME = "csrf_token";

    /** Name of the request header that must echo the cookie value. */
    static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    /** HTTP methods that never mutate server state — CSRF check is skipped. */
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE);

    /**
     * Pre-authentication paths where no CSRF cookie has been issued yet.
     * These are excluded from enforcement.
     */
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh");

    /**
     * Validates the Double Submit Cookie token on every mutating request.
     *
     * @param exchange the current server exchange
     * @param chain    the remaining filter chain
     * @return a {@link Mono} completing normally if the CSRF check passes,
     *         or completing with HTTP 403 if it fails
     */
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        final ServerHttpRequest request = exchange.getRequest();
        final HttpMethod method = request.getMethod();

        if (method == null || SAFE_METHODS.contains(method)) {
            return chain.filter(exchange);
        }

        final String path = request.getPath().value();
        if (EXCLUDED_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        final HttpCookie csrfCookie = request.getCookies().getFirst(CSRF_COOKIE_NAME);
        final String headerValue = request.getHeaders().getFirst(CSRF_HEADER_NAME);

        if (csrfCookie == null || csrfCookie.getValue().isBlank()
                || headerValue == null || headerValue.isBlank()) {
            log.warn("[CSRF] REJECTED | method={} path={} reason=MISSING_TOKEN",
                    method.name(), path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        if (!csrfCookie.getValue().equals(headerValue)) {
            log.warn("[CSRF] REJECTED | method={} path={} reason=TOKEN_MISMATCH",
                    method.name(), path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    /**
     * Returns {@code HIGHEST_PRECEDENCE + 2}, placing this filter immediately
     * after {@link SecurityHeadersFilter} ({@code HIGHEST_PRECEDENCE + 1}) and
     * before the downstream routing filters.
     *
     * @return the filter priority value
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
