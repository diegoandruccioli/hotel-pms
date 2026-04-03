package com.hotelpms.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Configures rate-limiting beans for the Spring Cloud Gateway.
 *
 * <p>
 * The {@code remoteAddrKeyResolver} bean is referenced by name in the
 * {@code application.yml} rate-limiter filter definition:
 * 
 * <pre>key-resolver: "#{@remoteAddrKeyResolver}"</pre>
 *
 * <p>
 * Using the client IP as the bucket key means each distinct IP address
 * receives its own independent token bucket, preventing a single attacker
 * from exhausting the global request quota of the authentication endpoint.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the rate-limit bucket key from the client's remote IP address.
     *
     * <p>
     * This is intentionally the simplest production-safe key resolver.
     * If the gateway sits behind a load-balancer or reverse proxy, consider
     * extracting the {@code X-Forwarded-For} header instead:
     * 
     * <pre>
     * Objects.requireNonNull(exchange.getRequest().getHeaders()
     *         .getFirst("X-Forwarded-For"), "X-Forwarded-For header missing")
     * </pre>
     *
     * @return a {@link KeyResolver} backed by the client remote address
     */
    @Bean
    public KeyResolver remoteAddrKeyResolver() {
        return exchange -> Mono.just(
                Objects.requireNonNull(
                        exchange.getRequest().getRemoteAddress(),
                        "Remote address must not be null").getAddress().getHostAddress());
    }
}
