package com.hotelpms.auth.service;

import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.ChangePasswordRequest;
import com.hotelpms.auth.dto.LoginRequest;
import com.hotelpms.auth.dto.RegisterRequest;

/**
 * Service interface for authentication operations.
 */
public interface AuthService {

    /**
     * Registers a new user.
     *
     * @param request the registration request containing user details
     * @return an {@link AuthResponse} containing a fresh access token and refresh token
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates a user and returns a new token pair.
     *
     * @param request the login request containing username and password
     * @return an {@link AuthResponse} containing a fresh access token and refresh token
     */
    AuthResponse login(LoginRequest request);

    /**
     * Validates the given refresh token, blacklists it, and issues a new token pair (rotation).
     *
     * <p>Implements T-AUTH-04: on every successful refresh the old refresh token
     * JTI is added to the Redis blacklist, preventing token reuse even if the
     * value is intercepted.</p>
     *
     * @param refreshToken the current refresh JWT read from the cookie
     * @return an {@link AuthResponse} with a new access token and a newly rotated refresh token
     */
    AuthResponse refresh(String refreshToken);

    /**
     * Blacklists the given refresh token so it cannot be used again (logout path).
     *
     * <p>Ignores tokens that are already expired or have an invalid signature —
     * they are inherently unusable and need no blacklist entry.</p>
     *
     * @param refreshToken the refresh JWT value to invalidate
     */
    void invalidateRefreshToken(String refreshToken);

    /**
     * Changes the password for the authenticated user and invalidates all existing sessions
     * (T-AUTH-04 residuo).
     *
     * <p>The caller must supply the current password as a second authentication factor,
     * preventing an attacker with a stolen access token from silently replacing credentials.
     * After a successful change, {@code UserAccount.tokenVersion} is incremented and the new
     * value is cached in Redis. Any previously issued refresh token carries the old {@code tv}
     * claim and will be rejected at the next rotation attempt.</p>
     *
     * <p>A fresh token pair (with the updated {@code tv}) is returned so the requesting
     * session remains active without forcing the owner to log in again.</p>
     *
     * @param username the authenticated user's username (extracted from the access token)
     * @param request  the change-password request containing current and new passwords
     * @return a fresh {@link AuthResponse} with new access and refresh tokens
     */
    AuthResponse changePassword(String username, ChangePasswordRequest request);
}
