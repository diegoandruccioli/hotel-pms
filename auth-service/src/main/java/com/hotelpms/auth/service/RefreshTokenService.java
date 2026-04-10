package com.hotelpms.auth.service;

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
}
