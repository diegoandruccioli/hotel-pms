package com.hotelpms.guest.security;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis-backed {@link NonceStore}. Uses {@code SETNX} semantics
 * ({@link org.springframework.data.redis.core.ValueOperations#setIfAbsent})
 * so the claim is atomic even if multiple instances of this service share the
 * same Redis (T-GW-08).
 */
public final class RedisNonceStore implements NonceStore {

    private static final String KEY_PREFIX = "internal-auth:nonce:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Constructs the store with the shared Redis client.
     *
     * @param redisTemplate the Spring-managed Redis client used to claim nonces
     */
    public RedisNonceStore(final StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean claim(final String nonce, final long ttlSeconds) {
        final Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + nonce, "1", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(claimed);
    }
}
