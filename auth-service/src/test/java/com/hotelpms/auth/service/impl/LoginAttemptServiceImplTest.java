package com.hotelpms.auth.service.impl;

import com.hotelpms.auth.service.LoginAttemptResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceImplTest {

    private static final String ATTEMPTS_PREFIX = "login:fail:";
    private static final String USERNAME = "admin";
    private static final String CLIENT_IP = "test-client-ip";
    private static final String OTHER_CLIENT_IP = "other-test-client-ip";
    private static final String UNKNOWN_SUFFIX = "unknown";
    private static final String ATTEMPTS_KEY = ATTEMPTS_PREFIX + USERNAME + ":" + CLIENT_IP;
    private static final String LOCK_KEY = "login:lock:" + USERNAME + ":" + CLIENT_IP;
    private static final String UNKNOWN_ATTEMPTS_KEY = ATTEMPTS_PREFIX + USERNAME + ":" + UNKNOWN_SUFFIX;
    private static final String OTHER_ATTEMPTS_KEY = ATTEMPTS_PREFIX + USERNAME + ":" + OTHER_CLIENT_IP;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final long RESIDUAL_TTL_SECONDS = 300L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private LoginAttemptServiceImpl loginAttemptService;

    // ─── T-AUTH-15: null/blank clientIp must never collapse the key ────────────

    @Test
    void recordFailureWithNullClientIpUsesUnknownNotLiteralNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(UNKNOWN_ATTEMPTS_KEY)).thenReturn(1L);

        loginAttemptService.recordFailure(USERNAME, null);

        verify(valueOps).increment(UNKNOWN_ATTEMPTS_KEY);
        verify(valueOps, Mockito.never()).increment(org.mockito.ArgumentMatchers.contains(":null"));
    }

    @Test
    void recordFailureWithBlankClientIpUsesUnknownBucket() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(UNKNOWN_ATTEMPTS_KEY)).thenReturn(1L);

        loginAttemptService.recordFailure(USERNAME, "   ");

        verify(valueOps).increment(UNKNOWN_ATTEMPTS_KEY);
    }

    @Test
    void recordFailureWithDifferentIpsUsesDistinctBuckets() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(1L);
        when(valueOps.increment(OTHER_ATTEMPTS_KEY)).thenReturn(1L);

        loginAttemptService.recordFailure(USERNAME, CLIENT_IP);
        loginAttemptService.recordFailure(USERNAME, OTHER_CLIENT_IP);

        verify(valueOps).increment(ATTEMPTS_KEY);
        verify(valueOps).increment(OTHER_ATTEMPTS_KEY);
    }

    // ─── getLockedUntil ───────────────────────────────────────────────────────

    @Test
    void lockedUntilIsEmptyWhenKeyAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(LOCK_KEY)).thenReturn(null);

        assertEquals(Optional.empty(), loginAttemptService.getLockedUntil(USERNAME, CLIENT_IP),
                "Should return empty when no lock key exists in Redis");
    }

    @Test
    void lockedUntilReturnsStoredInstantWhenLocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        final Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(15));
        when(valueOps.get(LOCK_KEY)).thenReturn(Long.toString(lockedUntil.toEpochMilli()));

        final Optional<Instant> result = loginAttemptService.getLockedUntil(USERNAME, CLIENT_IP);

        assertTrue(result.isPresent());
        assertEquals(lockedUntil.toEpochMilli(), result.get().toEpochMilli());
    }

    @Test
    void lockedUntilFailsOpenAndDeletesKeyWhenValueIsCorrupted() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(LOCK_KEY)).thenReturn("not-a-number");

        final Optional<Instant> result = loginAttemptService.getLockedUntil(USERNAME, CLIENT_IP);

        assertEquals(Optional.empty(), result,
                "A corrupted lock value must not lock the account out permanently");
        verify(redisTemplate).delete(LOCK_KEY);
    }

    // ─── recordFailure ────────────────────────────────────────────────────────

    @Test
    void recordFailureSetsTtlOnFirstAttempt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(1L);

        final LoginAttemptResult result = loginAttemptService.recordFailure(USERNAME, CLIENT_IP);

        assertEquals(1, result.attempts());
        assertFalse(result.locked(), "Should not be locked on the first failed attempt");
        verify(redisTemplate).expire(eq(ATTEMPTS_KEY), eq(LOCKOUT_DURATION));
    }

    @Test
    void recordFailureDoesNotResetTtlOnSubsequentAttempts() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(2L);
        when(redisTemplate.getExpire(ATTEMPTS_KEY)).thenReturn(RESIDUAL_TTL_SECONDS);

        final LoginAttemptResult result = loginAttemptService.recordFailure(USERNAME, CLIENT_IP);

        assertEquals(2, result.attempts());
        assertFalse(result.locked());
        verify(redisTemplate, Mockito.never()).expire(eq(ATTEMPTS_KEY), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordFailureRepairsMissingTtlOnSubsequentAttempt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(2L);
        // Simulates the expire() call from attempt 1 having failed to apply:
        // the counter survived the increment but carries no TTL (-1) or is
        // reported missing entirely (-2/null) by the Redis client.
        when(redisTemplate.getExpire(ATTEMPTS_KEY)).thenReturn(-1L);

        final LoginAttemptResult result = loginAttemptService.recordFailure(USERNAME, CLIENT_IP);

        assertEquals(2, result.attempts());
        verify(redisTemplate).expire(eq(ATTEMPTS_KEY), eq(LOCKOUT_DURATION));
    }

    @Test
    void recordFailureLocksPairWhenThresholdReached() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn((long) MAX_FAILED_ATTEMPTS);

        final LoginAttemptResult result = loginAttemptService.recordFailure(USERNAME, CLIENT_IP);

        assertEquals(MAX_FAILED_ATTEMPTS, result.attempts());
        assertTrue(result.locked(), "Should lock once MAX_FAILED_ATTEMPTS is reached");

        final List<Invocation> invocations = new ArrayList<>(
                Mockito.mockingDetails(valueOps).getInvocations());
        final Invocation setCall = invocations.stream()
                .filter(inv -> "set".equals(inv.getMethod().getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(LOCK_KEY, setCall.<String>getArgument(0), "Lock key must follow login:lock:<user>:<ip>");
        assertEquals(LOCKOUT_DURATION, setCall.<Duration>getArgument(2));
    }

    @Test
    void recordFailureTreatsNullIncrementAsFirstAttempt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(null);

        final LoginAttemptResult result = loginAttemptService.recordFailure(USERNAME, CLIENT_IP);

        assertEquals(1, result.attempts(), "A null Redis INCR result must be treated as attempt 1");
        assertFalse(result.locked());
    }

    // ─── reset ────────────────────────────────────────────────────────────────

    @Test
    void resetDeletesBothAttemptsAndLockKeys() {
        loginAttemptService.reset(USERNAME, CLIENT_IP);

        verify(redisTemplate).delete(ATTEMPTS_KEY);
        verify(redisTemplate).delete(LOCK_KEY);
    }
}
