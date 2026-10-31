package com.hotelpms.internalauth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RedisNonceStore} must turn a Redis failure into {@link NonceStoreUnavailableException}, so
 * {@link InternalAuthFilter} can answer 503. An unwrapped Spring Data exception escaped the filter, was
 * handled by no advice, and reached the client as a bare 403 after the full 60 s Lettuce timeout (found in a
 * live check, 2026-10-31): indistinguishable from a rejected signature.
 *
 * <p>The template is subclassed rather than mocked: the module has no mocking library, and failing
 * {@code opsForValue()} is exactly the point where a lost connection surfaces.
 */
class RedisNonceStoreTest {

    private static final String NONCE = "3f2c1a6e";
    private static final long TTL_SECONDS = 120;

    private static StringRedisTemplate failingWith(final DataAccessException failure) {
        return new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                throw failure;
            }
        };
    }

    @Test
    @DisplayName("A lost Redis connection is reported as NonceStoreUnavailableException, cause kept")
    void connectionFailureBecomesNonceStoreUnavailable() {
        final RedisConnectionFailureException failure = new RedisConnectionFailureException("Unable to connect");
        final RedisNonceStore store = new RedisNonceStore(failingWith(failure));

        assertThatThrownBy(() -> store.claim(NONCE, TTL_SECONDS))
                .isInstanceOf(NonceStoreUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    @DisplayName("A Redis command timeout is reported as NonceStoreUnavailableException")
    void commandTimeoutBecomesNonceStoreUnavailable() {
        final RedisNonceStore store = new RedisNonceStore(failingWith(new QueryTimeoutException("timed out")));

        assertThatThrownBy(() -> store.claim(NONCE, TTL_SECONDS))
                .isInstanceOf(NonceStoreUnavailableException.class)
                .hasCauseInstanceOf(QueryTimeoutException.class);
    }

    @Test
    @DisplayName("A stopped connection factory (IllegalStateException, not translated by Spring) is an outage too")
    void stoppedConnectionFactoryBecomesNonceStoreUnavailable() {
        final RedisNonceStore store = new RedisNonceStore(new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                throw new IllegalStateException("connection factory was destroyed");
            }
        });

        assertThatThrownBy(() -> store.claim(NONCE, TTL_SECONDS))
                .isInstanceOf(NonceStoreUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A failure that is neither a data-access problem nor a stopped factory is not disguised as an outage")
    void unrelatedFailuresPropagateUntouched() {
        final RedisNonceStore store = new RedisNonceStore(new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                throw new UnsupportedOperationException("programming error");
            }
        });

        assertThatThrownBy(() -> store.claim(NONCE, TTL_SECONDS))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("programming error");
    }
}
