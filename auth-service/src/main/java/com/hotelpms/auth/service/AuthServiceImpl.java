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

    private final UserAccountRepository userRepository;
    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Registers a new user.
     *
     * @param request the registration request
     * @return the auth response
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
        final String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token);
    }

    /**
     * Authenticates a user, enforcing a brute-force lockout policy.
     *
     * <p>After {@code MAX_FAILED_ATTEMPTS} consecutive failures the account is
     * locked for {@code LOCKOUT_DURATION}.  A successful login resets the
     * counter and clears the lock (T-AUTH-02).
     *
     * @param request the login request
     * @return the auth response containing a fresh JWT
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
        final String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token);
    }
}
