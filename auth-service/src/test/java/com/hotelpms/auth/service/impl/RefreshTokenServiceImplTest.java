package com.hotelpms.auth.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

        verify(valueOps).set(
                Objects.requireNonNull(eq(REDIS_KEY)),
                Objects.requireNonNull(eq("1")),
                Objects.requireNonNull(any(Duration.class)));
    }

    @Test
    void blacklistSkipsStorageWhenTokenIsAlreadyExpired() {
        final Instant pastExpiry = Instant.now().minusSeconds(1);

        refreshTokenService.blacklist(TEST_JTI, pastExpiry);

        verify(redisTemplate, never()).opsForValue();
        verify(valueOps, never()).set(
                Objects.requireNonNull(anyString()),
                Objects.requireNonNull(anyString()),
                Objects.requireNonNull(any(Duration.class)));
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
}
