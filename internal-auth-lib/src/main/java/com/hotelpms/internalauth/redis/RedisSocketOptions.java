package com.hotelpms.internalauth.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;

import java.time.Duration;

/**
 * Makes the Lettuce Redis client notice a peer that vanished without closing the connection.
 *
 * <p>If Redis disappears without a FIN/RST (a Docker network detached while hot, a host that suspended and resumed,
 * a partition) the socket stays {@code ESTABLISHED}: commands time out at 2 s but Lettuce sees no disconnect and never
 * reconnects. Reproduced live: 0 of 7 services recovered in over 8 minutes. Two things were missing:
 * <ul>
 *   <li><b>TCP keepalive</b> is off by default in Lettuce, and the kernel's own default starts after 2 hours. On an
 *       idle connection, 10 s idle plus 3 probes 5 s apart closes a dead one in about 25 s.</li>
 *   <li><b>TCP user timeout</b>: Linux sends no keepalive probe while data is in flight, and these services keep
 *       sending commands (readiness checks, the nonce claim on every internal request), so the kernel only gave up
 *       after {@code tcp_retries2}, about 15 minutes. {@code TCP_USER_TIMEOUT} closes the socket when sent data stays
 *       unacknowledged for 30 s. It needs the native epoll (or io_uring) transport: without it Lettuce logs a warning
 *       and keeps the keepalive only.</li>
 * </ul>
 * Once the socket is closed Lettuce reconnects, and {@link RedisDnsResolution} makes that reconnect find a Redis that
 * moved to another address.
 *
 * <p>Plain helper, not an auto-configuration: this library deliberately has none. Each service exposes it as a
 * {@code LettuceClientOptionsBuilderCustomizer} bean next to the other Redis customizer it already declares.
 */
public final class RedisSocketOptions {

    private static final Duration KEEPALIVE_IDLE = Duration.ofSeconds(10);
    private static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(5);
    private static final int KEEPALIVE_PROBES = 3;

    /** Slightly above idle + probes * interval (25 s), so the two mechanisms do not race each other. */
    private static final Duration TCP_USER_TIMEOUT = Duration.ofSeconds(30);

    private RedisSocketOptions() {
    }

    /**
     * Sets keepalive and TCP user timeout on the given client-options builder.
     *
     * <p>Spring Boot builds its own {@code SocketOptions} (carrying only the connect timeout) and applies the
     * customizers after it, so setting socket options here replaces Boot's: the connect timeout is therefore passed in
     * and set again.
     *
     * @param builder        the builder Spring Boot passes to its {@code LettuceClientOptionsBuilderCustomizer} beans
     * @param connectTimeout the configured {@code spring.data.redis.connect-timeout}, or {@code null} to keep Lettuce's
     *                       default
     */
    public static void apply(final ClientOptions.Builder builder, final Duration connectTimeout) {
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
        if (connectTimeout != null) {
            socketOptions.connectTimeout(connectTimeout);
        }
        builder.socketOptions(socketOptions.build());
    }
}
