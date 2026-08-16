package com.hotelpms.auth.service;

import java.time.Instant;
import java.util.Optional;

/**
 * Tracks failed login attempts and lockout state per (username, client IP) pair
 * (Finding #4, security-report.md).
 *
 * <p>Binding the lockout to the client IP alongside the username prevents an
 * unauthenticated attacker from permanently denying a known account (e.g. the
 * default {@code admin} user) to its legitimate owner: the attacker's own
 * repeated failures only lock out that attacker's IP, never the account as a
 * whole.</p>
 */
public interface LoginAttemptService {

    /**
     * Returns the lockout expiry for the given pair, if currently locked.
     *
     * @param username the account username
     * @param clientIp the trusted client IP (see {@code X-Client-IP}, gateway-injected)
     * @return the instant the lockout expires, or {@link Optional#empty()} if not locked
     */
    Optional<Instant> getLockedUntil(String username, String clientIp);

    /**
     * Records one failed login attempt for the given pair, locking it out once the
     * configured threshold is reached.
     *
     * @param username the account username
     * @param clientIp the trusted client IP
     * @return the resulting attempt count and lockout expiry (if now locked)
     */
    LoginAttemptResult recordFailure(String username, String clientIp);

    /**
     * Clears the failed-attempt counter and lockout for the given pair, called on
     * successful login.
     *
     * @param username the account username
     * @param clientIp the trusted client IP
     */
    void reset(String username, String clientIp);
}
