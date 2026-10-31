package com.hotelpms.gateway.config;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisClientConfigTest {

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
}
