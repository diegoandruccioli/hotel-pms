package com.hotelpms.internalauth.security;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis-backed {@link NonceStore}. Uses {@code SETNX} semantics
 * ({@link org.springframework.data.redis.core.ValueOperations#setIfAbsent})
 * so the claim is atomic even if multiple instances of a service share the
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
        try {
            final Boolean claimed = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + nonce, "1", Objects.requireNonNull(Duration.ofSeconds(ttlSeconds)));
            return Boolean.TRUE.equals(claimed);
        } catch (final DataAccessException | IllegalStateException e) {
            // Connection refused/lost, command timeout, or a connection factory that was stopped (Spring
            // does not translate that last one): the check did not run. Say so, so the filter can fail
            // closed with a 503 instead of letting the exception escape and reach the client as a bare 403.
            throw new NonceStoreUnavailableException("Redis nonce store unavailable", e);
        }
    }
}
