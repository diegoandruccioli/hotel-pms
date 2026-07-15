package com.hotelpms.notification.security;

/**
 * Store for one-time nonces used by {@link InternalAuthFilter} to prevent
 * replay attacks on internal HMAC-signed requests (T-GW-08).
 */
@FunctionalInterface
public interface NonceStore {

    /**
     * Atomically claims the given nonce for {@code ttlSeconds} seconds.
     *
     * @param nonce      the nonce to claim
     * @param ttlSeconds how long to remember this nonce before expiry
     * @return {@code true} if the nonce was not yet seen and is now claimed;
     *         {@code false} if it was already present (replay detected)
     */
    boolean claim(String nonce, long ttlSeconds);
}
