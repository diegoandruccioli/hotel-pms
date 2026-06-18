package com.hotelpms.frontdesk.stays.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configures in-memory Caffeine caches for the Portale Alloggiati Web lookup tables.
 *
 * <p>The lookup data is loaded once at application startup by
 * {@link AlloggiatiLookupDataLoader} and does not change during the lifetime of the JVM.
 * Caching PK lookups ({@code findXByCodice}) eliminates repeated DB round-trips
 * during report generation, which calls the lookup service once per guest field.
 *
 * <p>Cache TTL is 24 hours; entries are also evicted when the JVM shuts down.
 * To force a reload (e.g. after updating the lookup tables), restart the service.
 *
 * <p>{@code @EnableCaching} is declared once on {@code FrontdeskApplication} (also
 * needed by the rooms domain's RoomType cache) — not repeated here.
 */
@Configuration
public class AlloggiatiCacheConfig {

    /** Cache name for {@code AlloggiatiComune} PK lookups. */
    public static final String CACHE_COMUNI = "alloggiati-comuni";

    /** Cache name for {@code AlloggiatiStato} PK lookups. */
    public static final String CACHE_STATI = "alloggiati-stati";

    /** Cache name for {@code AlloggiatiTipdoc} PK lookups. */
    public static final String CACHE_TIPDOC = "alloggiati-tipdoc";

    private static final int MAX_COMUNI = 10_000;   // portal has ~7 500 comuni
    private static final int MAX_STATI = 300;        // portal has ~249 stati
    private static final int MAX_TIPDOC = 200;       // portal has ~95 tipdoc
    private static final long TTL_HOURS = 24L;

    /**
     * Creates the Caffeine-backed {@link CacheManager} with per-cache size limits.
     *
     * @return configured cache manager
     */
    @Bean
    @SuppressWarnings("null")
    public CacheManager alloggiatiCacheManager() {
        final CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(CACHE_COMUNI,
                Caffeine.newBuilder()
                        .maximumSize(MAX_COMUNI)
                        .expireAfterWrite(TTL_HOURS, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(CACHE_STATI,
                Caffeine.newBuilder()
                        .maximumSize(MAX_STATI)
                        .expireAfterWrite(TTL_HOURS, TimeUnit.HOURS)
                        .build());
        manager.registerCustomCache(CACHE_TIPDOC,
                Caffeine.newBuilder()
                        .maximumSize(MAX_TIPDOC)
                        .expireAfterWrite(TTL_HOURS, TimeUnit.HOURS)
                        .build());
        return manager;
    }
}
