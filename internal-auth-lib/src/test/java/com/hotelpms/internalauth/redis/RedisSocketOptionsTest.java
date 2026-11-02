package com.hotelpms.internalauth.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Why this exists: when the peer disappears without FIN/RST (a Docker network detached while hot, a host that
 * suspended and resumed) the socket stays {@code ESTABLISHED}; commands time out at 2 s but Lettuce never reconnects,
 * because TCP keepalive is off by default and the kernel only gives up on unacknowledged data after about 15 minutes
 * ({@code tcp_retries2}). Reproduced live: 0 of 7 services recovered in over 8 minutes.
 */
class RedisSocketOptionsTest {

    private static final Duration BOOT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration KEEPALIVE_IDLE = Duration.ofSeconds(10);
    private static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(5);
    private static final int KEEPALIVE_PROBES = 3;
    private static final Duration TCP_USER_TIMEOUT = Duration.ofSeconds(30);

    private static SocketOptions socketOptionsFor(final Duration connectTimeout) {
        final ClientOptions.Builder builder = ClientOptions.builder();
        RedisSocketOptions.apply(builder, connectTimeout);
        return builder.build().getSocketOptions();
    }

    @Test
    @DisplayName("Lettuce's default is keepalive off and no user timeout, which is what left the socket half open")
    void lettuceDefaultsDetectNothing() {
        final SocketOptions defaults = ClientOptions.builder().build().getSocketOptions();

        assertThat(defaults.isKeepAlive()).isFalse();
        assertThat(defaults.isEnableTcpUserTimeout()).isFalse();
    }

    @Test
    @DisplayName("Keepalive is on and a silent peer is noticed after about 25 s (10 s idle, 3 probes, 5 s apart)")
    void enablesKeepAliveForAnIdleConnection() {
        final SocketOptions options = socketOptionsFor(BOOT_CONNECT_TIMEOUT);

        assertThat(options.isKeepAlive()).isTrue();
        assertThat(options.getKeepAlive().isEnabled()).isTrue();
        assertThat(options.getKeepAlive().getIdle()).isEqualTo(KEEPALIVE_IDLE);
        assertThat(options.getKeepAlive().getInterval()).isEqualTo(KEEPALIVE_INTERVAL);
        assertThat(options.getKeepAlive().getCount()).isEqualTo(KEEPALIVE_PROBES);
    }

    @Test
    @DisplayName("TCP user timeout is on: Linux sends no keepalive probe while data is in flight, so a busy connection "
            + "needs it to be closed when its data stays unacknowledged")
    void enablesTcpUserTimeoutForABusyConnection() {
        final SocketOptions options = socketOptionsFor(BOOT_CONNECT_TIMEOUT);

        assertThat(options.isEnableTcpUserTimeout()).isTrue();
        assertThat(options.getTcpUserTimeout().getTcpUserTimeout()).isEqualTo(TCP_USER_TIMEOUT);
    }

    @Test
    @DisplayName("The connect timeout Spring Boot configured survives: replacing the socket options must not drop it")
    void keepsTheConnectTimeoutSpringBootAlreadySet() {
        assertThat(socketOptionsFor(BOOT_CONNECT_TIMEOUT).getConnectTimeout()).isEqualTo(BOOT_CONNECT_TIMEOUT);
    }

    @Test
    @DisplayName("Without a configured connect timeout Lettuce's own default stays in place")
    void leavesTheLettuceDefaultWhenNoConnectTimeoutIsConfigured() {
        assertThat(socketOptionsFor(null).getConnectTimeout()).isEqualTo(SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION);
    }
}
