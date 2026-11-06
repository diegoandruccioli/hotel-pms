package com.hotelpms.internalauth.redis;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DnsResolvers;

/**
 * Makes the Lettuce Redis client resolve the Redis host again on every reconnect.
 *
 * <p>Since Lettuce 4.4 the default is {@link DnsResolvers#UNRESOLVED}: the name is left to Netty, whose DNS client
 * caches each record for its TTL ({@code netty-resolver-dns} is on every service's classpath). When Redis came back
 * on another address (a {@code docker compose} recreate), none of the 7 services that use it reconnected for over 11
 * minutes, although the name already resolved to the new IP and the port was open (reproduced live, 2026-10-31):
 * the client kept retrying the old address, readiness stayed down and every internal call failed.
 *
 * <p>{@link DnsResolvers#JVM_DEFAULT} resolves through {@link java.net.InetAddress} on every connection attempt,
 * so the JVM's own cache (30 s by default, 10 s for failures) is the only one left.
 *
 * <p>Plain helper, not an auto-configuration: this library deliberately has none. Each service exposes it as a
 * {@code ClientResourcesBuilderCustomizer} bean next to the {@code NonceStore} it already declares.
 */
public final class RedisDnsResolution {

    private RedisDnsResolution() {
    }

    /**
     * Applies the JVM resolver to the given client-resources builder.
     *
     * @param builder the builder Spring Boot passes to its {@code ClientResourcesBuilderCustomizer} beans
     */
    public static void apply(final ClientResources.Builder builder) {
        builder.dnsResolver(DnsResolvers.JVM_DEFAULT);
    }
}
