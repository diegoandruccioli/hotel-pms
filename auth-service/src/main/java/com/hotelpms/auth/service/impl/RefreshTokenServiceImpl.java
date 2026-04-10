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
}
