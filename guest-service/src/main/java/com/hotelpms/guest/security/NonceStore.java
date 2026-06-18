package com.hotelpms.guest.security;

/**
 * Claims a single-use nonce to detect replay of internal HMAC-signed requests
 * (T-GW-08).
 */
@FunctionalInterface
public interface NonceStore {

    /**
     * Atomically claims the given nonce for the given time-to-live.
     *
     * @param nonce      the nonce value to claim
     * @param ttlSeconds how long the claim is remembered, in seconds
     * @return {@code true} if the nonce had not been claimed before (fresh),
     *         {@code false} if it was already claimed (replay)
     */
    boolean claim(String nonce, long ttlSeconds);
}
