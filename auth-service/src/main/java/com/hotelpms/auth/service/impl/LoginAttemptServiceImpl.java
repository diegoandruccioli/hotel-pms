package com.hotelpms.auth.service.impl;

import com.hotelpms.auth.service.LoginAttemptResult;
import com.hotelpms.auth.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redis-backed implementation of {@link LoginAttemptService}.
 *
 * <p>Failed-attempt counters are stored under {@code login:fail:<username>:<ip>}
 * with a TTL equal to the lockout window, so a counter that never reaches the
 * threshold self-expires. Once the threshold is reached, a separate
 * {@code login:lock:<username>:<ip>} key holds the lockout expiry, also
 * self-expiring — no background cleanup required.</p>
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final String ATTEMPTS_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";

    private final StringRedisTemplate redisTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Instant> getLockedUntil(final String username, final String clientIp) {
        final String raw = redisTemplate.opsForValue().get(lockKey(username, clientIp));
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli(Long.parseLong(raw)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginAttemptResult recordFailure(final String username, final String clientIp) {
        final String attemptsKey = attemptsKey(username, clientIp);
        final Long newAttempts = redisTemplate.opsForValue().increment(attemptsKey);
        final long attempts = newAttempts == null ? 1L : newAttempts;
        if (attempts == 1L) {
            redisTemplate.expire(attemptsKey, LOCKOUT_DURATION);
        }
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            final Instant lockedUntil = Instant.now().plus(LOCKOUT_DURATION);
            redisTemplate.opsForValue().set(lockKey(username, clientIp),
                    Long.toString(lockedUntil.toEpochMilli()), LOCKOUT_DURATION);
            return new LoginAttemptResult((int) attempts, lockedUntil);
        }
        return new LoginAttemptResult((int) attempts, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset(final String username, final String clientIp) {
        redisTemplate.delete(attemptsKey(username, clientIp));
        redisTemplate.delete(lockKey(username, clientIp));
    }

    private static String attemptsKey(final String username, final String clientIp) {
        return ATTEMPTS_PREFIX + username + ":" + clientIp;
    }

    private static String lockKey(final String username, final String clientIp) {
        return LOCK_PREFIX + username + ":" + clientIp;
    }
}
