package com.hotelpms.auth.service.impl;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final String TEST_JTI = "test-jti-uuid";
    private static final String REDIS_KEY = "rt:blacklist:" + TEST_JTI;
    private static final long FUTURE_TTL_SECONDS = 3600L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Test
    void blacklistStoresKeyWithTtlWhenTokenIsActive() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        final Instant futureExpiry = Instant.now().plusSeconds(FUTURE_TTL_SECONDS);

        refreshTokenService.blacklist(TEST_JTI, futureExpiry);

        final List<Invocation> invocations = new ArrayList<>(
                Mockito.mockingDetails(valueOps).getInvocations());
        assertEquals(1, invocations.size(), "set() must be called exactly once");
        final Invocation setCall = invocations.get(0);
        assertEquals(REDIS_KEY, setCall.<String>getArgument(0), "key must be the blacklist key");
        assertEquals("1", setCall.<String>getArgument(1), "value must be '1'");
        assertTrue(setCall.<Duration>getArgument(2).getSeconds() > 0L, "TTL must be positive");
    }

    @Test
    void blacklistSkipsStorageWhenTokenIsAlreadyExpired() {
        final Instant pastExpiry = Instant.now().minusSeconds(1);

        refreshTokenService.blacklist(TEST_JTI, pastExpiry);

        verify(redisTemplate, never()).opsForValue();
        Mockito.verifyNoInteractions(valueOps);
    }

    @Test
    void isBlacklistedReturnsTrueWhenKeyExistsInRedis() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(Boolean.TRUE);

        assertTrue(refreshTokenService.isBlacklisted(TEST_JTI),
                "Should return true when JTI is present in Redis");
    }

    @Test
    void isBlacklistedReturnsFalseWhenKeyAbsentInRedis() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(Boolean.FALSE);

        assertFalse(refreshTokenService.isBlacklisted(TEST_JTI),
                "Should return false when JTI is not in Redis");
    }

    @Test
    void isBlacklistedReturnsFalseWhenRedisReturnsNull() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(null);

        assertFalse(refreshTokenService.isBlacklisted(TEST_JTI),
                "Should return false when Redis returns null (key absent)");
    }

    // ─── Token Version (T-AUTH-04 residuo) ───────────────────────────────────

    @Test
    void storeTokenVersionWritesCorrectKeyAndValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        final Duration ttl = Duration.ofDays(7);

        refreshTokenService.storeTokenVersion("alice", 3, ttl);

        final List<Invocation> invocations = new ArrayList<>(
                Mockito.mockingDetails(valueOps).getInvocations());
        assertEquals(1, invocations.size(), "set() must be called exactly once");
        final Invocation setCall = invocations.get(0);
        assertEquals("user:tv:alice", setCall.<String>getArgument(0),
                "Key must follow the user:tv:<username> pattern");
        assertEquals("3", setCall.<String>getArgument(1),
                "Value must be the string representation of the version");
        assertEquals(ttl, setCall.<Duration>getArgument(2),
                "TTL must match the supplied duration");
    }

    @Test
    void getTokenVersionReturnsStoredValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("user:tv:alice")).thenReturn("2");

        assertEquals(2, refreshTokenService.getTokenVersion("alice"),
                "Should return the integer value stored in Redis");
    }

    @Test
    void getTokenVersionReturnsNegativeOneWhenKeyAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("user:tv:bob")).thenReturn(null);

        assertEquals(-1, refreshTokenService.getTokenVersion("bob"),
                "Should return -1 when the Redis key does not exist");
    }

    @Test
    void getTokenVersionReturnsNegativeOneWhenValueIsNotNumeric() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("user:tv:corrupt")).thenReturn("not-a-number");

        assertEquals(-1, refreshTokenService.getTokenVersion("corrupt"),
                "Should return -1 and not throw when Redis value is non-numeric");
    }
}
