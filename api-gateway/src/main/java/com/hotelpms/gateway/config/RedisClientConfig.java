package com.hotelpms.gateway.config;

import io.lettuce.core.resource.DnsResolvers;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the gateway's Redis client (rate limiter) resolve the Redis host again on every reconnect.
 *
 * <p>Since Lettuce 4.4 the default is {@link DnsResolvers#UNRESOLVED}: the name is left to Netty, whose DNS client
 * caches each record for its TTL. When Redis came back on another address, the client kept retrying the old one
 * for over 11 minutes although the name already resolved to the new IP (reproduced live, 2026-10-31).
 * {@link DnsResolvers#JVM_DEFAULT} resolves through {@link java.net.InetAddress} on every connection attempt, so
 * only the JVM's own short cache is left.
 *
 * <p>Same setting as {@code RedisDnsResolution} in {@code internal-auth-lib}, which the servlet services use; the
 * gateway has no dependency on that library, so it carries its own two lines.
 */
@Configuration
public class RedisClientConfig {

    /**
     * Customizes the Lettuce client resources Spring Boot builds for the Redis connection.
     *
     * @return the customizer that switches the resolver to the JVM one
     */
    @Bean
    public static ClientResourcesBuilderCustomizer redisDnsResolutionCustomizer() {
        return builder -> builder.dnsResolver(DnsResolvers.JVM_DEFAULT);
    }
}
