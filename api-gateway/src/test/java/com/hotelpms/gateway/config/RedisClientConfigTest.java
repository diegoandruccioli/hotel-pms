package com.hotelpms.gateway.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisClientConfigTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration KEEPALIVE_IDLE = Duration.ofSeconds(10);
    private static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(5);
    private static final int KEEPALIVE_PROBES = 3;
    private static final Duration TCP_USER_TIMEOUT = Duration.ofSeconds(30);

    @Test
    @DisplayName("The gateway's Redis client resolves the host again on every reconnect (JVM resolver)")
    void customizerAppliesTheJvmResolver() {
        final DefaultClientResources.Builder builder = DefaultClientResources.builder();

        RedisClientConfig.redisDnsResolutionCustomizer().customize(builder);

        final ClientResources resources = builder.build();
        try {
            assertThat(resources.dnsResolver()).isSameAs(DnsResolvers.JVM_DEFAULT);
        } finally {
            resources.shutdown();
        }
    }

    @Test
    @DisplayName("A half-open Redis connection is noticed: keepalive for an idle one, TCP user timeout for a busy one, "
            + "and the connect timeout Spring Boot configured is kept")
    void socketOptionsDetectDeadPeersAndKeepTheConnectTimeout() {
        final DataRedisProperties properties = new DataRedisProperties();
        properties.setConnectTimeout(CONNECT_TIMEOUT);
        final ClientOptions.Builder builder = ClientOptions.builder();

        final StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("redisProperties", properties);

        RedisClientConfig.redisSocketOptionsCustomizer(beans.getBeanProvider(DataRedisProperties.class))
                .customize(builder);

        final SocketOptions options = builder.build().getSocketOptions();
        assertThat(options.getKeepAlive().isEnabled()).isTrue();
        assertThat(options.getKeepAlive().getIdle()).isEqualTo(KEEPALIVE_IDLE);
        assertThat(options.getKeepAlive().getInterval()).isEqualTo(KEEPALIVE_INTERVAL);
        assertThat(options.getKeepAlive().getCount()).isEqualTo(KEEPALIVE_PROBES);
        assertThat(options.isEnableTcpUserTimeout()).isTrue();
        assertThat(options.getTcpUserTimeout().getTcpUserTimeout()).isEqualTo(TCP_USER_TIMEOUT);
        assertThat(options.getConnectTimeout()).isEqualTo(CONNECT_TIMEOUT);
    }
}
