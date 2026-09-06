package com.hotelpms.auth.service.impl;

import com.hotelpms.auth.service.LoginAttemptResult;
import com.hotelpms.auth.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *
 * <p>{@code username}/{@code clientIp} are normalized to the literal {@code "unknown"}
 * before being folded into a key (T-AUTH-15): with raw concatenation, a {@code null}
 * argument silently produces the literal key suffix {@code :null} instead of throwing,
 * collapsing the per-{@code (username, ip)} lockout binding (T-AUTH-12) into one shared
 * bucket for every caller that omits it. The primary defense is the caller resolving a
 * real client IP before it gets here ({@code AuthController.resolveClientIp}); this
 * normalization is the second layer so the key format can never regress silently.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final String ATTEMPTS_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";
    private static final String UNKNOWN = "unknown";

    private final StringRedisTemplate redisTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Instant> getLockedUntil(final String username, final String clientIp) {
        final String lockKey = lockKey(username, clientIp);
        final String raw = redisTemplate.opsForValue().get(lockKey);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(raw)));
        } catch (final NumberFormatException e) {
            // Corrupted lock value: fail open rather than lock the account out
            // permanently on a value this code never wrote. The failed-attempt
            // counter (a separate key) keeps counting regardless.
            log.warn("[AUTH] LOCKOUT_KEY_CORRUPTED | key={} | value={}", lockKey, raw);
            redisTemplate.delete(lockKey);
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginAttemptResult recordFailure(final String username, final String clientIp) {
        final String attemptsKey = attemptsKey(username, clientIp);
        final Long newAttempts = redisTemplate.opsForValue().increment(attemptsKey);
        final long attempts = newAttempts == null ? 1L : newAttempts;
        // Repair the TTL whenever it's missing, not just on the very first attempt:
        // if the expire() below ever failed to apply (Redis hiccup between the two
        // calls), a counter left without a TTL would never self-expire and the
        // account would stay locked forever once it crossed the threshold.
        final Long ttl = redisTemplate.getExpire(attemptsKey);
        if (attempts == 1L || ttl == null || ttl < 0) {
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
        return ATTEMPTS_PREFIX + normalize(username) + ":" + normalize(clientIp);
    }

    private static String lockKey(final String username, final String clientIp) {
        return LOCK_PREFIX + normalize(username) + ":" + normalize(clientIp);
    }

    private static String normalize(final String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
