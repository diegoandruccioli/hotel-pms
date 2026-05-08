package com.hotelpms.auth.service.impl;

import com.hotelpms.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Redis-backed implementation of {@link RefreshTokenService}.
 *
 * <p>JTIs are stored under the key {@code rt:blacklist:<jti>} with a TTL equal
 * to the token's remaining lifetime.  When the token would have expired anyway,
 * the Redis entry disappears automatically — no background cleanup required.</p>
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    /** Key prefix for blacklisted refresh-token JTIs in Redis. */
    private static final String PREFIX = "rt:blacklist:";

    /** Key prefix for per-user token version cache entries in Redis. */
    private static final String TV_PREFIX = "user:tv:";

    private final StringRedisTemplate redisTemplate;

    /**
     * {@inheritDoc}
     *
     * <p>If {@code expiresAt} is already in the past the token is inherently
     * invalid, so no Redis entry is created.</p>
     */
    @Override
    public void blacklist(final String jti, final Instant expiresAt) {
        final long ttlSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(PREFIX + jti, "1", Objects.requireNonNull(Duration.ofSeconds(ttlSeconds)));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBlacklisted(final String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeTokenVersion(final String username, final int version, final Duration ttl) {
        redisTemplate.opsForValue().set(TV_PREFIX + username, Objects.requireNonNull(Integer.toString(version)),
                Objects.requireNonNull(ttl));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code -1} when the Redis key is absent (user has not yet logged in
     * with the new code, or the key has expired). The value {@code -1} signals
     * "unknown" to the caller so the version check can be safely skipped.</p>
     */
    @Override
    public int getTokenVersion(final String username) {
        final String raw = redisTemplate.opsForValue().get(TV_PREFIX + username);
        if (raw == null) {
            return -1;
        }
        try {
            return Integer.parseInt(raw);
        } catch (final NumberFormatException e) {
            return -1;
        }
    }
}
