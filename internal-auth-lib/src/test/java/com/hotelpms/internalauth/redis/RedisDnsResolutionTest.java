package com.hotelpms.internalauth.redis;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Why this exists: with {@code netty-resolver-dns} on the classpath (it is, on every service) Lettuce resolves the
 * Redis host with Netty's own DNS client, which caches each record for its TTL. After Redis moved to another IP
 * (a {@code docker compose} recreate) none of the 7 services reconnected for over 11 minutes, even though the name
 * already resolved to the new address and the port was reachable (reproduced live, 2026-10-31).
 */
class RedisDnsResolutionTest {

    @Test
    @DisplayName("Lettuce's own default resolver is the Netty one that caches, which is what made clients go stale")
    void lettuceDefaultIsNotTheJvmResolver() {
        final ClientResources defaults = DefaultClientResources.create();
        try {
            assertThat(defaults.dnsResolver()).isNotSameAs(DnsResolvers.JVM_DEFAULT);
        } finally {
            defaults.shutdown();
        }
    }

    @Test
    @DisplayName("Once applied, the host is resolved again on every reconnect through the JVM resolver")
    void appliesTheJvmResolver() {
        final DefaultClientResources.Builder builder = DefaultClientResources.builder();

        RedisDnsResolution.apply(builder);

        final ClientResources resources = builder.build();
        try {
            assertThat(resources.dnsResolver()).isSameAs(DnsResolvers.JVM_DEFAULT);
        } finally {
            resources.shutdown();
        }
    }
}
