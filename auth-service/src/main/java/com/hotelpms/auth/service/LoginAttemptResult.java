package com.hotelpms.auth.service;

import java.time.Instant;

/**
 * Outcome of recording one failed login attempt for a (username, client IP) pair.
 *
 * @param attempts    the failed-attempt count for this pair after the increment
 * @param lockedUntil the instant the pair becomes unlocked, or {@code null} if this
 *                     failure did not reach the lockout threshold
 */
public record LoginAttemptResult(int attempts, Instant lockedUntil) {

    /**
     * Returns {@code true} if this failure reached the lockout threshold.
     *
     * @return {@code true} when {@link #lockedUntil} is not {@code null}
     */
    public boolean locked() {
        return lockedUntil != null;
    }
}
