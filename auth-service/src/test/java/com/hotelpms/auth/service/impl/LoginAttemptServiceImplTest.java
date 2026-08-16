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

    private static final String USERNAME = "admin";
    private static final String CLIENT_IP = "test-client-ip";
    private static final String ATTEMPTS_KEY = "login:fail:" + USERNAME + ":" + CLIENT_IP;
    private static final String LOCK_KEY = "login:lock:" + USERNAME + ":" + CLIENT_IP;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private LoginAttemptServiceImpl loginAttemptService;

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

        final LoginAttemptResult result = loginAttemptService.recordFailure(USERNAME, CLIENT_IP);

        assertEquals(2, result.attempts());
        assertFalse(result.locked());
        verify(redisTemplate, Mockito.never()).expire(eq(ATTEMPTS_KEY), org.mockito.ArgumentMatchers.any());
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
