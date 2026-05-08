package com.hotelpms.auth.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for managing the refresh token blacklist (T-AUTH-04).
 *
 * <p>When a refresh token is consumed (rotation) or explicitly revoked (logout),
 * its JTI is stored in Redis until the token naturally expires, preventing reuse
 * even if the token value is intercepted.</p>
 */
public interface RefreshTokenService {

    /**
     * Adds the given token JTI to the blacklist until it naturally expires.
     *
     * <p>The Redis entry is created with a TTL equal to the token's remaining
     * lifetime, so the blacklist entry is self-cleaning.</p>
     *
     * @param jti       the JWT ID to blacklist (never {@code null})
     * @param expiresAt the instant at which the token naturally expires
     */
    void blacklist(String jti, Instant expiresAt);

    /**
     * Returns {@code true} if the given JTI is currently blacklisted.
     *
     * @param jti the JWT ID to check (never {@code null})
     * @return {@code true} if the JTI is in the blacklist, {@code false} otherwise
     */
    boolean isBlacklisted(String jti);

    /**
     * Stores the current token version for the given user in Redis (T-AUTH-04 residuo).
     *
     * <p>The key {@code user:tv:<username>} is written with the supplied TTL so
     * that it expires once no valid token for the user can exist anymore. On
     * password change the value is overwritten with the incremented version,
     * causing subsequent {@link #getTokenVersion} calls to return the new value
     * and triggering a mismatch rejection in {@code AuthServiceImpl.refresh()}.</p>
     *
     * @param username the username (Redis key suffix)
     * @param version  the current {@code tokenVersion} of the user account
     * @param ttl      how long the key should live (typically the refresh token lifetime)
     */
    void storeTokenVersion(String username, int version, Duration ttl);

    /**
     * Returns the token version stored in Redis for the given user.
     *
     * <p>Returns {@code -1} when the key does not exist (user has not logged in
     * since the feature was deployed, or the key expired). The caller must treat
     * {@code -1} as "version unknown" and skip the version check.</p>
     *
     * @param username the username (Redis key suffix)
     * @return the stored token version, or {@code -1} if the key is absent
     */
    int getTokenVersion(String username);
}
