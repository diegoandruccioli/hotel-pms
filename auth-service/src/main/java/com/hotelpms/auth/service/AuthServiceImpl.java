package com.hotelpms.auth.service;

import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.dto.AuthResponse;
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

        log.info("[AUTH] REGISTER_SUCCESS | user={}", user.getUsername());
        final String accessToken = jwtService.generateToken(user.getUsername(), user.getRole(), user.getHotelId());
        final String refreshToken = jwtService.generateRefreshToken(user.getUsername(), user.getRole(), user.getHotelId());
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

        log.info("[AUTH] LOGIN_SUCCESS | user={}", user.getUsername());
        final String accessToken = jwtService.generateToken(user.getUsername(), user.getRole(), user.getHotelId());
        final String refreshToken = jwtService.generateRefreshToken(user.getUsername(), user.getRole(), user.getHotelId());
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

        // Blacklist the consumed token before issuing the new pair
        final Instant expiresAt = jwtService.extractExpirationInstant(refreshToken);
        refreshTokenService.blacklist(jti, expiresAt);

        log.info("[AUTH] REFRESH_SUCCESS | user={}", username);
        final String newAccessToken = jwtService.generateToken(username, user.getRole(), user.getHotelId());
        final String newRefreshToken = jwtService.generateRefreshToken(username, user.getRole(), user.getHotelId());
        return new AuthResponse(newAccessToken, newRefreshToken);
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
