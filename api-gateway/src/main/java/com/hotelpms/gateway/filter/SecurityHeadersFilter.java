package com.hotelpms.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global gateway filter that appends defensive HTTP security headers to every
 * response emitted by the API Gateway, providing a centralised hardening layer
 * for all routes regardless of the downstream microservice.
 *
 * <p>The following headers are applied (OWASP A05 — Security Misconfiguration):
 * <ul>
 *   <li><b>Strict-Transport-Security</b> — instructs browsers to use HTTPS for
 *       one year, preventing protocol-downgrade attacks.</li>
 *   <li><b>X-Content-Type-Options: nosniff</b> — disables MIME-type sniffing so
 *       browsers honour the declared {@code Content-Type} and cannot be tricked
 *       into executing a JSON response as a script.</li>
 *   <li><b>X-Frame-Options: DENY</b> — blocks all iframe embedding, eliminating
 *       clickjacking vectors.</li>
 *   <li><b>Referrer-Policy: no-referrer</b> — suppresses the {@code Referer}
 *       header, preventing URL leakage to external origins.</li>
 *   <li><b>Permissions-Policy</b> — explicitly disables camera, microphone and
 *       geolocation APIs that the PMS application does not require.</li>
 *   <li><b>Content-Security-Policy: default-src 'none'</b> — API responses are
 *       JSON payloads that should never be rendered as HTML; this CSP ensures
 *       nothing can be fetched or executed if such a response is opened directly
 *       in a browser tab (defence in depth). The SPA-level CSP is handled
 *       separately by the frontend nginx configuration (T-FE-04).</li>
 * </ul>
 *
 * <p>Implements threat <b>T-GW-03</b> from the project threat model.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    /** Response header name for HTTP Strict Transport Security. */
    static final String HEADER_HSTS = "Strict-Transport-Security";

    /** Response header name for content-type sniffing prevention. */
    static final String HEADER_XCTO = "X-Content-Type-Options";

    /** Response header name for frame-embedding prevention (clickjacking). */
    static final String HEADER_XFO = "X-Frame-Options";

    /** Response header name for referrer information control. */
    static final String HEADER_REFERRER = "Referrer-Policy";

    /** Response header name for browser permissions policy. */
    static final String HEADER_PERMISSIONS = "Permissions-Policy";

    /** Response header name for content security policy. */
    static final String HEADER_CSP = "Content-Security-Policy";

    /** Enforce HTTPS for one year, including subdomains. */
    static final String VALUE_HSTS = "max-age=31536000; includeSubDomains";

    /** Disable MIME-type sniffing. */
    static final String VALUE_XCTO = "nosniff";

    /** Block all iframe embedding. */
    static final String VALUE_XFO = "DENY";

    /** Suppress Referer header on all navigations. */
    static final String VALUE_REFERRER = "no-referrer";

    /** Disable sensitive browser APIs unused by the PMS application. */
    static final String VALUE_PERMISSIONS = "camera=(), microphone=(), geolocation=()";

    /**
     * API-level CSP: deny everything — JSON responses must never be rendered as
     * HTML. {@code frame-ancestors 'none'} reinforces the X-Frame-Options header
     * for modern browsers.
     */
    static final String VALUE_CSP = "default-src 'none'; frame-ancestors 'none'";

    /**
     * Registers a {@code beforeCommit} callback on the reactive response so that
     * all security headers are written immediately before the HTTP response headers
     * are flushed to the network. This guarantees delivery regardless of whether
     * the downstream response body is streaming or buffered.
     *
     * @param exchange the current server exchange
     * @param chain    the remaining filter chain
     * @return a {@link Mono} that completes when the filter chain is done
     */
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            final HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set(HEADER_HSTS, VALUE_HSTS);
            headers.set(HEADER_XCTO, VALUE_XCTO);
            headers.set(HEADER_XFO, VALUE_XFO);
            headers.set(HEADER_REFERRER, VALUE_REFERRER);
            headers.set(HEADER_PERMISSIONS, VALUE_PERMISSIONS);
            headers.set(HEADER_CSP, VALUE_CSP);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    /**
     * Returns the filter order. {@code HIGHEST_PRECEDENCE + 1} places this filter
     * at the very front of the global filter chain so security headers are
     * registered before any other filter runs and cannot be suppressed by
     * downstream logic.
     *
     * @return the filter priority value
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
