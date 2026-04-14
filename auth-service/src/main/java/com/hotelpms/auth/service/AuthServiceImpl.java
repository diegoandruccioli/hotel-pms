package com.hotelpms.auth.service;

import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.ChangePasswordRequest;
import com.hotelpms.auth.dto.LoginRequest;
import com.hotelpms.auth.dto.RegisterRequest;
import com.hotelpms.auth.exception.AccountLockedException;
import com.hotelpms.auth.exception.BadCredentialsException;
import com.hotelpms.auth.exception.DuplicateResourceException;
import com.hotelpms.auth.mapper.UserAccountMapper;
import com.hotelpms.auth.repository.UserAccountRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Implementation of {@link AuthService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

    /**
     * TTL for the Redis token-version key {@code user:tv:<username>}.
     *
     * <p>Must be at least as long as the refresh token lifetime so that the version
     * check remains effective for the entire window during which old tokens could
     * be replayed. Kept in sync with {@code jwt.refresh-expiration} in config.</p>
     */
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final UserAccountRepository userRepository;
    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Registers a new user and issues a token pair.
     *
     * @param request the registration request
     * @return the auth response containing access + refresh tokens
     */
    @Override
    @Transactional
    public AuthResponse register(final RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("USERNAME_ALREADY_EXISTS");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("EMAIL_ALREADY_EXISTS");
        }

        final UserAccount user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        userRepository.save(user);

        // Cache the initial token version (0) so the tv check in refresh() is active
        // from the very first token rotation.
        refreshTokenService.storeTokenVersion(user.getUsername(), user.getTokenVersion(),
                REFRESH_TOKEN_TTL);

        log.info("[AUTH] REGISTER_SUCCESS | user={}", user.getUsername());
        final String accessToken = jwtService.generateToken(user.getUsername(), user.getRole(),
                user.getHotelId(), user.getTokenVersion());
        final String refreshToken = jwtService.generateRefreshToken(user.getUsername(), user.getRole(),
                user.getHotelId(), user.getTokenVersion());
        return new AuthResponse(accessToken, refreshToken);
    }

    /**
     * Authenticates a user, enforcing a brute-force lockout policy.
     *
     * <p>After {@code MAX_FAILED_ATTEMPTS} consecutive failures the account is
     * locked for {@code LOCKOUT_DURATION}.  A successful login resets the
     * counter and clears the lock (T-AUTH-02).
     *
     * @param request the login request
     * @return the auth response containing a fresh access token and refresh token
     */
    @Override
    @Transactional
    public AuthResponse login(final LoginRequest request) {
        final UserAccount user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    log.warn("[AUTH] LOGIN_FAILED | user={} | reason=USER_NOT_FOUND", request.username());
                    return new BadCredentialsException("INVALID_CREDENTIALS");
                });

        if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
            log.warn("[AUTH] LOGIN_BLOCKED | user={} | reason=ACCOUNT_LOCKED | until={}",
                    user.getUsername(), user.getLockedUntil());
            throw new AccountLockedException("ACCOUNT_TEMPORARILY_LOCKED");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            final int newAttempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(newAttempts);
            if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
                log.warn("[AUTH] ACCOUNT_LOCKED | user={} | attempts={} | until={}",
                        user.getUsername(), newAttempts, user.getLockedUntil());
            } else {
                log.warn("[AUTH] LOGIN_FAILED | user={} | reason=BAD_PASSWORD | attempts={}",
                        user.getUsername(), newAttempts);
            }
            userRepository.save(user);
            throw new BadCredentialsException("INVALID_CREDENTIALS");
        }

        // Lazy rehash: upgrade stored hash to current cost factor if needed (T-AUTH-03)
        final boolean needsRehash = passwordEncoder.upgradeEncoding(user.getPasswordHash());
        final boolean needsReset = user.getFailedAttempts() > 0;
        if (needsRehash) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            log.info("[AUTH] PASSWORD_REHASHED | user={}", user.getUsername());
        }
        if (needsReset) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        }
        if (needsRehash || needsReset) {
            userRepository.save(user);
        }

        // Refresh the Redis token-version cache on every successful login so that
        // the key TTL is reset and the tv check remains active.
        refreshTokenService.storeTokenVersion(user.getUsername(), user.getTokenVersion(),
                REFRESH_TOKEN_TTL);

        log.info("[AUTH] LOGIN_SUCCESS | user={}", user.getUsername());
        final String accessToken = jwtService.generateToken(user.getUsername(), user.getRole(),
                user.getHotelId(), user.getTokenVersion());
        final String refreshToken = jwtService.generateRefreshToken(user.getUsername(), user.getRole(),
                user.getHotelId(), user.getTokenVersion());
        return new AuthResponse(accessToken, refreshToken);
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
                user.getHotelId(), user.getTokenVersion());
        final String newRefreshToken = jwtService.generateRefreshToken(username, user.getRole(),
                user.getHotelId(), user.getTokenVersion());
        return new AuthResponse(newAccessToken, newRefreshToken);
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
                    return new BadCredentialsException("INVALID_CREDENTIALS");
                });

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("[AUTH] CHANGE_PASSWORD_REJECTED | reason=BAD_CURRENT_PASSWORD | user={}", username);
            throw new BadCredentialsException("INVALID_CURRENT_PASSWORD");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Overwrite Redis key with the new version to invalidate all previously issued tokens
        refreshTokenService.storeTokenVersion(username, user.getTokenVersion(), REFRESH_TOKEN_TTL);

        log.info("[AUTH] PASSWORD_CHANGED | user={} | newTokenVersion={}", username,
                user.getTokenVersion());

        final String accessToken = jwtService.generateToken(username, user.getRole(),
                user.getHotelId(), user.getTokenVersion());
        final String refreshToken = jwtService.generateRefreshToken(username, user.getRole(),
                user.getHotelId(), user.getTokenVersion());
        return new AuthResponse(accessToken, refreshToken);
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
}
