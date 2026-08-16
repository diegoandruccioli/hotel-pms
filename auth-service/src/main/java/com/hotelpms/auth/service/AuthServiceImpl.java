package com.hotelpms.auth.service;

import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.ChangePasswordRequest;
import com.hotelpms.auth.dto.LoginRequest;
import com.hotelpms.auth.exception.AccountLockedException;
import com.hotelpms.auth.exception.BadCredentialsException;
import com.hotelpms.auth.repository.UserAccountRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Implementation of {@link AuthService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
    private static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    private static final String DUMMY_PASSWORD_FOR_TIMING = "dummy-password-for-constant-time-check";

    /**
     * TTL for the Redis token-version key {@code user:tv:<username>}.
     *
     * <p>Must be at least as long as the refresh token lifetime so that the version
     * check remains effective for the entire window during which old tokens could
     * be replayed. Kept in sync with {@code jwt.refresh-expiration} in config.</p>
     */
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

    /**
     * Lazily-memoized throwaway hash (Finding #9, security-report.md) — computed
     * once against {@link #passwordEncoder}, so its cost/format always matches
     * whatever encoder is actually configured, with no hardcoded hash to keep in
     * sync by hand.
     */
    private volatile String dummyPasswordHash;

    /**
     * Authenticates a user, enforcing a brute-force lockout policy keyed on
     * (username, clientIp).
     *
     * <p>After {@code MAX_FAILED_ATTEMPTS} consecutive failures for the same pair,
     * that pair is locked for the configured lockout window (T-AUTH-02). Binding
     * the lockout to the client IP as well as the username — rather than username
     * alone — prevents an unauthenticated attacker from permanently denying a
     * known account (e.g. {@code admin}) to its legitimate owner: the attacker's
     * failures only lock out the attacker's own IP (Finding #4, security-report.md).
     * A successful login resets the counter and clears the lock for that pair.
     *
     * @param request  the login request
     * @param clientIp the trusted client IP (gateway-injected {@code X-Client-IP})
     * @return the auth response containing a fresh access token and refresh token
     */
    @Override
    @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})
    public AuthResponse login(final LoginRequest request, final String clientIp) {
        final Optional<UserAccount> maybeUser = userRepository.findByUsername(request.username());
        final UserAccount user = maybeUser.orElse(null);

        // Constant-time defense (Finding #9, plus the locked-vs-wrong-password
        // follow-up): pay the same Argon2id cost for every outcome — unknown user,
        // locked account, or wrong password — computed unconditionally, before any
        // branch below decides which exception to throw, so none of them can be
        // distinguished by response time. The result is discarded whenever the
        // user doesn't exist or the account is locked; it only ever drives a
        // decision in the "user found and not locked" branch further down.
        final boolean passwordMatches = passwordEncoder.matches(
                request.password(), user != null ? user.getPasswordHash() : dummyHash());

        if (user == null) {
            log.warn("[AUTH] LOGIN_FAILED | user={} | reason=USER_NOT_FOUND", request.username());
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        final Optional<Instant> lockedUntil = loginAttemptService.getLockedUntil(user.getUsername(), clientIp);
        if (lockedUntil.isPresent() && Instant.now().isBefore(lockedUntil.get())) {
            log.warn("[AUTH] LOGIN_BLOCKED | user={} | ip={} | reason=ACCOUNT_LOCKED | until={}",
                    user.getUsername(), clientIp, lockedUntil.get());
            throw new AccountLockedException("ACCOUNT_TEMPORARILY_LOCKED");
        }

        if (!passwordMatches) {
            final LoginAttemptResult result = loginAttemptService.recordFailure(user.getUsername(), clientIp);
            if (result.locked()) {
                log.warn("[AUTH] ACCOUNT_LOCKED | user={} | ip={} | attempts={} | until={}",
                        user.getUsername(), clientIp, result.attempts(), result.lockedUntil());
            } else {
                log.warn("[AUTH] LOGIN_FAILED | user={} | ip={} | reason=BAD_PASSWORD | attempts={}",
                        user.getUsername(), clientIp, result.attempts());
            }
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        loginAttemptService.reset(user.getUsername(), clientIp);

        // Lazy rehash: upgrade stored hash to current cost factor if needed (T-AUTH-03)
        final boolean needsRehash = passwordEncoder.upgradeEncoding(user.getPasswordHash());
        if (needsRehash) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            log.info("[AUTH] PASSWORD_REHASHED | user={}", user.getUsername());
            userRepository.save(user);
        }

        // Refresh the Redis token-version cache on every successful login so that
        // the key TTL is reset and the tv check remains active.
        refreshTokenService.storeTokenVersion(user.getUsername(), user.getTokenVersion(),
                REFRESH_TOKEN_TTL);

        log.info("[AUTH] LOGIN_SUCCESS | user={} | mustChangePassword={}", user.getUsername(),
                user.isMustChangePassword());
        final String accessToken = jwtService.generateToken(user.getUsername(), user.getRole(),
                user.getHotelId(), user.getTokenVersion(), user.isMustChangePassword());
        final String refreshToken = jwtService.generateRefreshToken(user.getUsername(), user.getRole(),
                user.getHotelId(), user.getTokenVersion(), user.isMustChangePassword());
        return new AuthResponse(accessToken, refreshToken, user.isMustChangePassword());
    }

    /**
     * Validates the refresh token, blacklists its JTI, and issues a new token pair (T-AUTH-04).
     *
     * @param refreshToken the current refresh JWT
     * @return a new {@link AuthResponse} with rotated tokens
     */
    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(final String refreshToken) {
        if (!jwtService.isRefreshToken(refreshToken)) {
            log.warn("[AUTH] REFRESH_REJECTED | reason=NOT_REFRESH_TOKEN");
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN);
        }

        final String jti = jwtService.extractJti(refreshToken);
        if (jti == null || refreshTokenService.isBlacklisted(jti)) {
            log.warn("[AUTH] REFRESH_REJECTED | reason=TOKEN_BLACKLISTED | jti={}", jti);
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN);
        }

        final String username = jwtService.extractUsername(refreshToken);
        final UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("[AUTH] REFRESH_REJECTED | reason=USER_NOT_FOUND | user={}", username);
                    return new BadCredentialsException(INVALID_REFRESH_TOKEN);
                });

        // Token-version check (T-AUTH-04 residuo): reject tokens issued before a password change.
        // storedTv = -1 means the Redis key is absent (user logged in before this feature was
        // deployed) → skip the check for graceful migration.
        // jwtTv = -1 means the token has no "tv" claim (old token without the claim) → mismatch
        // against any positive storedTv → correctly rejected.
        final int storedTv = refreshTokenService.getTokenVersion(username);
        final int jwtTv = jwtService.extractTokenVersion(refreshToken);
        if (storedTv >= 0 && jwtTv != storedTv) {
            log.warn("[AUTH] REFRESH_REJECTED | reason=TOKEN_VERSION_MISMATCH | user={} | jwtTv={} | storedTv={}",
                    username, jwtTv, storedTv);
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN);
        }

        // Blacklist the consumed token before issuing the new pair
        final Instant expiresAt = jwtService.extractExpirationInstant(refreshToken);
        refreshTokenService.blacklist(jti, expiresAt);

        log.info("[AUTH] REFRESH_SUCCESS | user={}", username);
        final String newAccessToken = jwtService.generateToken(username, user.getRole(),
                user.getHotelId(), user.getTokenVersion(), user.isMustChangePassword());
        final String newRefreshToken = jwtService.generateRefreshToken(username, user.getRole(),
                user.getHotelId(), user.getTokenVersion(), user.isMustChangePassword());
        return new AuthResponse(newAccessToken, newRefreshToken, user.isMustChangePassword());
    }

    /**
     * Changes the authenticated user's password and revokes all existing sessions
     * by incrementing {@code tokenVersion} (T-AUTH-04 residuo).
     *
     * <p>Steps:
     * <ol>
     *   <li>Re-verify identity with {@code currentPassword} (second factor against
     *       stolen-token attacks).</li>
     *   <li>Hash and store the new password.</li>
     *   <li>Increment {@code tokenVersion} in the database.</li>
     *   <li>Update the Redis cache key {@code user:tv:<username>} with the new version,
     *       so that any subsequent {@code refresh()} call carrying the old {@code tv}
     *       claim is immediately rejected.</li>
     *   <li>Issue a new token pair (with the updated {@code tv}) so the requesting
     *       session stays active without forcing the owner to log in again.</li>
     * </ol>
     * </p>
     *
     * @param username the authenticated user's username
     * @param request  the change-password request containing current and new passwords
     * @return a fresh {@link AuthResponse} with new access and refresh tokens
     */
    @Override
    @Transactional
    public AuthResponse changePassword(final String username, final ChangePasswordRequest request) {
        final UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("[AUTH] CHANGE_PASSWORD_REJECTED | reason=USER_NOT_FOUND | user={}", username);
                    return new BadCredentialsException(INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("[AUTH] CHANGE_PASSWORD_REJECTED | reason=BAD_CURRENT_PASSWORD | user={}", username);
            throw new BadCredentialsException("INVALID_CURRENT_PASSWORD");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Overwrite Redis key with the new version to invalidate all previously issued tokens
        refreshTokenService.storeTokenVersion(username, user.getTokenVersion(), REFRESH_TOKEN_TTL);

        log.info("[AUTH] PASSWORD_CHANGED | user={} | newTokenVersion={}", username,
                user.getTokenVersion());

        final String accessToken = jwtService.generateToken(username, user.getRole(),
                user.getHotelId(), user.getTokenVersion(), user.isMustChangePassword());
        final String refreshToken = jwtService.generateRefreshToken(username, user.getRole(),
                user.getHotelId(), user.getTokenVersion(), user.isMustChangePassword());
        return new AuthResponse(accessToken, refreshToken, false);
    }

    /**
     * Blacklists the given refresh token on logout (T-AUTH-04).
     *
     * <p>If the token is already expired or has an invalid signature it is
     * inherently unusable, so no action is taken.</p>
     *
     * @param refreshToken the refresh JWT value to invalidate
     */
    @Override
    public void invalidateRefreshToken(final String refreshToken) {
        try {
            final String jti = jwtService.extractJti(refreshToken);
            final Instant expiresAt = jwtService.extractExpirationInstant(refreshToken);
            if (jti != null) {
                refreshTokenService.blacklist(jti, expiresAt);
                log.info("[AUTH] REFRESH_TOKEN_INVALIDATED | jti={}", jti);
            }
        } catch (final JwtException | IllegalArgumentException e) {
            log.debug("[AUTH] REFRESH_TOKEN_INVALIDATION_SKIPPED | reason=INVALID_TOKEN");
        }
    }

    /**
     * Returns a throwaway password hash for the constant-time defense in
     * {@link #login}, computing it once on first use and reusing it afterward.
     *
     * @return a valid hash under whatever encoding {@link #passwordEncoder} is
     *         currently configured with
     */
    private String dummyHash() {
        String hash = dummyPasswordHash;
        if (hash == null) {
            hash = passwordEncoder.encode(DUMMY_PASSWORD_FOR_TIMING);
            dummyPasswordHash = hash;
        }
        return hash;
    }
}
