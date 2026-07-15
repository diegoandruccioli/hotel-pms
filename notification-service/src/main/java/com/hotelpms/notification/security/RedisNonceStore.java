package com.hotelpms.notification.security;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis-backed nonce store for internal HMAC anti-replay (T-GW-08).
 *
 * <p>Uses SET NX (set-if-not-exists) with TTL so Redis atomically ensures
 * a nonce can only be claimed once within its validity window.
 */
public final class RedisNonceStore implements NonceStore {

    private static final String KEY_PREFIX = "internal-auth:nonce:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Constructs the store with the shared Redis client.
     *
     * @param redisTemplate the Spring Redis client
     */
    public RedisNonceStore(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** {@inheritDoc} */
    @Override
    public boolean claim(final String nonce, final long ttlSeconds) {
        final Boolean result = redisTemplate
                .opsForValue()
                .setIfAbsent(KEY_PREFIX + nonce, "1", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(result);
    }
}
