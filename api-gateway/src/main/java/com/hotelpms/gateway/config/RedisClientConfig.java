package com.hotelpms.gateway.config;

import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.DnsResolvers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Makes the gateway's Redis client (rate limiter) resolve the Redis host again on every reconnect.
 *
 * <p>Since Lettuce 4.4 the default is {@link DnsResolvers#UNRESOLVED}: the name is left to Netty, whose DNS client
 * caches each record for its TTL. When Redis came back on another address, the client kept retrying the old one
 * for over 11 minutes although the name already resolved to the new IP (reproduced live, 2026-10-31).
 * {@link DnsResolvers#JVM_DEFAULT} resolves through {@link java.net.InetAddress} on every connection attempt, so
 * only the JVM's own short cache is left.
 *
 * <p>It also makes the client notice a peer that vanished without closing the connection (a Docker network detached
 * while hot, a host that suspended and resumed): the socket stays {@code ESTABLISHED}, commands time out but Lettuce
 * never reconnects. Keepalive (off by default in Lettuce, 2 h in the kernel) covers an idle connection; Linux sends no
 * probe while data is in flight, so a busy one needs {@code TCP_USER_TIMEOUT}, which closes the socket when sent data
 * stays unacknowledged. That one needs the native epoll transport, which the gateway has.
 *
 * <p>Same settings as {@code RedisDnsResolution} and {@code RedisSocketOptions} in {@code internal-auth-lib}, which
 * the servlet services use; the gateway has no dependency on that library, so it carries its own copy.
 */
@Configuration
public class RedisClientConfig {

    private static final Duration KEEPALIVE_IDLE = Duration.ofSeconds(10);
    private static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(5);
    private static final int KEEPALIVE_PROBES = 3;

    /** Slightly above idle + probes * interval (25 s), so the two mechanisms do not race each other. */
    private static final Duration TCP_USER_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Customizes the Lettuce client resources Spring Boot builds for the Redis connection.
     *
     * @return the customizer that switches the resolver to the JVM one
     */
    @Bean
    public static ClientResourcesBuilderCustomizer redisDnsResolutionCustomizer() {
        return builder -> builder.dnsResolver(DnsResolvers.JVM_DEFAULT);
    }

    /**
     * Sets keepalive and TCP user timeout on the Lettuce client options. Spring Boot builds its own socket options
     * (carrying only the connect timeout) and applies the customizers after it, so this replaces them: the configured
     * connect timeout is set again.
     *
     * @param properties the Spring Boot Redis properties, for the configured connect timeout; optional because a
     *                   web slice test loads this class without the Redis auto-configuration
     * @return the customizer Spring Boot applies to the Lettuce client options
     */
    @Bean
    public static LettuceClientOptionsBuilderCustomizer redisSocketOptionsCustomizer(
            final ObjectProvider<DataRedisProperties> properties) {
        return builder -> {
            final DataRedisProperties redis = properties.getIfAvailable();
            final SocketOptions.Builder socketOptions = SocketOptions.builder()
                    .keepAlive(SocketOptions.KeepAliveOptions.builder()
                            .enable()
                            .idle(KEEPALIVE_IDLE)
                            .interval(KEEPALIVE_INTERVAL)
                            .count(KEEPALIVE_PROBES)
                            .build())
                    .tcpUserTimeout(SocketOptions.TcpUserTimeoutOptions.builder()
                            .enable()
                            .tcpUserTimeout(TCP_USER_TIMEOUT)
                            .build());
            if (redis != null && redis.getConnectTimeout() != null) {
                socketOptions.connectTimeout(redis.getConnectTimeout());
            }
            builder.socketOptions(socketOptions.build());
        };
    }
}
