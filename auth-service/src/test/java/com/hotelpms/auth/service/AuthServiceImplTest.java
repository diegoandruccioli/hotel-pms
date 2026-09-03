package com.hotelpms.auth.service;

import com.hotelpms.auth.domain.Role;
import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.ChangePasswordRequest;
import com.hotelpms.auth.dto.LoginRequest;
import com.hotelpms.auth.exception.AccountLockedException;
import com.hotelpms.auth.exception.BadCredentialsException;
import com.hotelpms.auth.repository.UserAccountRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String TEST_USER = "testuser";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String RAW_PASSWORD = "rawpassword";
    private static final String HASHED_PASSWORD = "hashedpassword";
    private static final String MOCK_TOKEN = "mockJwtToken";
    private static final String MOCK_REFRESH_TOKEN = "mockRefreshToken";
    private static final String MOCK_NEW_REFRESH_TOKEN = "mockNewRefreshToken";
    private static final String TEST_JTI = "test-jti-uuid";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long FUTURE_TTL_SECONDS = 3600L;
    private static final String TEST_IP = "test-client-ip";
    private static final String DUMMY_HASH_VALUE = "dummy-hash-value";
    private static final UUID TEST_HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final int TEST_TOKEN_VERSION = 0;
    private static final String NEW_PASSWORD = "newpassword";
    private static final String WRONG_PASSWORD = "wrongpassword";

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthServiceImpl authService;

    private UserAccount testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = UserAccount.builder()
                .username(TEST_USER)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.GUEST)
                .hotelId(TEST_HOTEL_ID)
                .active(true)
                .build();

        loginRequest = new LoginRequest(TEST_USER, RAW_PASSWORD);
    }

    @Test
    void loginSuccess() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_TOKEN);
        when(jwtService.generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_REFRESH_TOKEN);

        final AuthResponse response = authService.login(loginRequest, TEST_IP);

        assertNotNull(response, "Response should not be null");
        assertEquals(MOCK_TOKEN, response.token(), "Token should match mocked one");
        assertEquals(MOCK_REFRESH_TOKEN, response.refreshToken(), "Refresh token should match mocked one");
    }

    @Test
    void loginPropagatesMustChangePasswordIntoJwtClaims() {
        final UserAccount userWithTempPassword = UserAccount.builder()
                .username(TEST_USER)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.RECEPTIONIST)
                .hotelId(TEST_HOTEL_ID)
                .active(true)
                .mustChangePassword(true)
                .build();
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(userWithTempPassword));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), eq(true)))
                .thenReturn(MOCK_TOKEN);
        when(jwtService.generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), eq(true)))
                .thenReturn(MOCK_REFRESH_TOKEN);

        final AuthResponse response = authService.login(loginRequest, TEST_IP);

        assertEquals(MOCK_TOKEN, response.token(),
                "BUG-5: generateToken must be called with mustChangePassword=true so the gateway can enforce it");
        assertNotNull(response.mustChangePassword());
    }

    @Test
    void loginThrowsWhenUserNotFound() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH_VALUE);

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest, TEST_IP),
                "Should throw BadCredentialsException when user is not found");

        verify(passwordEncoder).matches(eq(RAW_PASSWORD), eq(DUMMY_HASH_VALUE));
    }

    @Test
    void loginReusesTheSameDummyHashAcrossMultipleUnknownUsernames() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH_VALUE);

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest, TEST_IP));
        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest, TEST_IP));

        verify(passwordEncoder, times(1)).encode(anyString());
        verify(passwordEncoder, times(2)).matches(eq(RAW_PASSWORD), eq(DUMMY_HASH_VALUE));
    }

    @Test
    void loginThrowsWhenPasswordMismatch() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);
        when(loginAttemptService.recordFailure(TEST_USER, TEST_IP))
                .thenReturn(new LoginAttemptResult(1, null));

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest, TEST_IP),
                "Should throw BadCredentialsException when password does not match");
    }

    @Test
    void loginThrowsWhenAccountIsLockedButStillPaysArgon2idCost() {
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(testUser));
        when(loginAttemptService.getLockedUntil(TEST_USER, TEST_IP))
                .thenReturn(Optional.of(Instant.now().plus(Duration.ofMinutes(10))));

        assertThrows(AccountLockedException.class, () -> authService.login(loginRequest, TEST_IP),
                "Should throw AccountLockedException when account is locked");

        // Locked-vs-wrong-password timing follow-up (to #8/#9): the password check
        // now runs unconditionally even on the locked branch, so timing doesn't
        // distinguish "locked" from "wrong password" — but its result must never
        // be used to decide anything here (no recordFailure on this branch).
        verify(passwordEncoder).matches(RAW_PASSWORD, HASHED_PASSWORD);
        verify(loginAttemptService, never()).recordFailure(anyString(), anyString());
    }

    @Test
    void loginRecordsFailureOnWrongPassword() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);
        when(loginAttemptService.recordFailure(TEST_USER, TEST_IP))
                .thenReturn(new LoginAttemptResult(1, null));

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest, TEST_IP));

        verify(loginAttemptService).recordFailure(TEST_USER, TEST_IP);
    }

    @Test
    void loginWithNullClientIpOnWrongPasswordThrowsCredentialsNotNpe() {
        // clientIp is an optional gateway-injected header (X-Client-IP) and can be
        // null if the request reaches auth-service without it; sanitizeForLog must
        // not NPE on that null before logging the failed-login line.
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);
        when(loginAttemptService.recordFailure(TEST_USER, null))
                .thenReturn(new LoginAttemptResult(1, null));

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest, null),
                "A wrong-password login with no clientIp must still resolve to a clean 401, not an NPE");
    }

    @Test
    void loginWithNullClientIpOnLockedAccountThrowsAccountLockedNotNpe() {
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(testUser));
        when(loginAttemptService.getLockedUntil(TEST_USER, null))
                .thenReturn(Optional.of(Instant.now().plus(Duration.ofMinutes(10))));

        assertThrows(AccountLockedException.class, () -> authService.login(loginRequest, null),
                "A locked-account login with no clientIp must still resolve to AccountLockedException, not an NPE");
    }

    @Test
    void loginLocksPairAfterMaxFailedAttempts() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);
        final Instant lockedUntil = Instant.now().plus(Duration.ofMinutes(15));
        when(loginAttemptService.recordFailure(TEST_USER, TEST_IP))
                .thenReturn(new LoginAttemptResult(MAX_FAILED_ATTEMPTS, lockedUntil));

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest, TEST_IP));

        verify(loginAttemptService).recordFailure(TEST_USER, TEST_IP);
    }

    @Test
    void loginRehashesPasswordWhenEncodingNeedsUpgrade() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(passwordEncoder.upgradeEncoding(HASHED_PASSWORD)).thenReturn(true);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("rehashed_password");
        when(jwtService.generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_TOKEN);
        when(jwtService.generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_REFRESH_TOKEN);

        final AuthResponse response = authService.login(loginRequest, TEST_IP);

        assertNotNull(response);
        assertEquals(MOCK_TOKEN, response.token());
        assertEquals("rehashed_password", testUser.getPasswordHash(),
                "Password hash must be upgraded to the current cost factor");
        verify(userRepository).save(Objects.requireNonNull(testUser));
    }

    @Test
    void loginSkipsRehashWhenEncodingIsUpToDate() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(passwordEncoder.upgradeEncoding(HASHED_PASSWORD)).thenReturn(false);
        when(jwtService.generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_TOKEN);
        when(jwtService.generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_REFRESH_TOKEN);

        authService.login(loginRequest, TEST_IP);

        verify(userRepository).findByUsername(anyString());
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void loginResetsAttemptsOnSuccess() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_TOKEN);
        when(jwtService.generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_REFRESH_TOKEN);

        final AuthResponse response = authService.login(loginRequest, TEST_IP);

        assertNotNull(response);
        assertEquals(MOCK_TOKEN, response.token());

        verify(loginAttemptService).reset(TEST_USER, TEST_IP);
        verify(userRepository, never()).save(any(UserAccount.class));
    }

    // ─── T-AUTH-04: Refresh Token Rotation ───────────────────────────────────

    @Test
    void refreshSuccessIssuesNewTokenPair() {
        when(jwtService.isRefreshToken(MOCK_REFRESH_TOKEN)).thenReturn(true);
        when(jwtService.extractJti(MOCK_REFRESH_TOKEN)).thenReturn(TEST_JTI);
        when(refreshTokenService.isBlacklisted(TEST_JTI)).thenReturn(false);
        when(jwtService.extractUsername(MOCK_REFRESH_TOKEN)).thenReturn(TEST_USER);
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(testUser));
        // Token-version check: stored and JWT values match → allow
        when(refreshTokenService.getTokenVersion(TEST_USER)).thenReturn(TEST_TOKEN_VERSION);
        when(jwtService.extractTokenVersion(MOCK_REFRESH_TOKEN)).thenReturn(TEST_TOKEN_VERSION);
        when(jwtService.extractExpirationInstant(MOCK_REFRESH_TOKEN))
                .thenReturn(Instant.now().plusSeconds(FUTURE_TTL_SECONDS));
        when(jwtService.generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_TOKEN);
        when(jwtService.generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_NEW_REFRESH_TOKEN);

        final AuthResponse response = authService.refresh(MOCK_REFRESH_TOKEN);

        assertNotNull(response, "Response must not be null");
        assertEquals(MOCK_TOKEN, response.token(), "New access token must be returned");
        assertEquals(MOCK_NEW_REFRESH_TOKEN, response.refreshToken(), "New refresh token must be returned");
        verify(refreshTokenService).blacklist(eq(TEST_JTI), any(Instant.class));
    }

    @Test
    void refreshThrowsWhenTokenVersionMismatch() {
        when(jwtService.isRefreshToken(MOCK_REFRESH_TOKEN)).thenReturn(true);
        when(jwtService.extractJti(MOCK_REFRESH_TOKEN)).thenReturn(TEST_JTI);
        when(refreshTokenService.isBlacklisted(TEST_JTI)).thenReturn(false);
        when(jwtService.extractUsername(MOCK_REFRESH_TOKEN)).thenReturn(TEST_USER);
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(testUser));
        // Simulate password change: Redis has version 1, JWT still carries version 0
        when(refreshTokenService.getTokenVersion(TEST_USER)).thenReturn(1);
        when(jwtService.extractTokenVersion(MOCK_REFRESH_TOKEN)).thenReturn(0);

        assertThrows(BadCredentialsException.class, () -> authService.refresh(MOCK_REFRESH_TOKEN),
                "Should reject refresh tokens whose tv claim diverges from Redis-cached version");
        verify(refreshTokenService, never()).blacklist(anyString(), any(Instant.class));
    }

    @Test
    void refreshSkipsVersionCheckWhenRedisKeyAbsent() {
        // storedTv = -1 means Redis key absent (pre-feature token) → check skipped
        when(jwtService.isRefreshToken(MOCK_REFRESH_TOKEN)).thenReturn(true);
        when(jwtService.extractJti(MOCK_REFRESH_TOKEN)).thenReturn(TEST_JTI);
        when(refreshTokenService.isBlacklisted(TEST_JTI)).thenReturn(false);
        when(jwtService.extractUsername(MOCK_REFRESH_TOKEN)).thenReturn(TEST_USER);
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(testUser));
        when(refreshTokenService.getTokenVersion(TEST_USER)).thenReturn(-1);
        when(jwtService.extractExpirationInstant(MOCK_REFRESH_TOKEN))
                .thenReturn(Instant.now().plusSeconds(FUTURE_TTL_SECONDS));
        when(jwtService.generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_TOKEN);
        when(jwtService.generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_NEW_REFRESH_TOKEN);

        final AuthResponse response = authService.refresh(MOCK_REFRESH_TOKEN);

        assertNotNull(response, "Should succeed when Redis key is absent (graceful migration)");
        verify(refreshTokenService).blacklist(eq(TEST_JTI), any(Instant.class));
    }

    @Test
    void refreshThrowsWhenTokenIsNotRefreshType() {
        when(jwtService.isRefreshToken(MOCK_REFRESH_TOKEN)).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.refresh(MOCK_REFRESH_TOKEN),
                "Should reject tokens that are not of typ=refresh");
        verify(refreshTokenService, never()).blacklist(anyString(), any(Instant.class));
    }

    @Test
    void refreshThrowsWhenJtiIsBlacklisted() {
        when(jwtService.isRefreshToken(MOCK_REFRESH_TOKEN)).thenReturn(true);
        when(jwtService.extractJti(MOCK_REFRESH_TOKEN)).thenReturn(TEST_JTI);
        when(refreshTokenService.isBlacklisted(TEST_JTI)).thenReturn(true);

        assertThrows(BadCredentialsException.class, () -> authService.refresh(MOCK_REFRESH_TOKEN),
                "Should reject blacklisted refresh tokens");
        verify(refreshTokenService, never()).blacklist(anyString(), any(Instant.class));
    }

    @Test
    void refreshThrowsWhenUserNotFound() {
        when(jwtService.isRefreshToken(MOCK_REFRESH_TOKEN)).thenReturn(true);
        when(jwtService.extractJti(MOCK_REFRESH_TOKEN)).thenReturn(TEST_JTI);
        when(refreshTokenService.isBlacklisted(TEST_JTI)).thenReturn(false);
        when(jwtService.extractUsername(MOCK_REFRESH_TOKEN)).thenReturn(TEST_USER);
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.refresh(MOCK_REFRESH_TOKEN),
                "Should reject refresh tokens for deleted/inactive users");
        verify(refreshTokenService, never()).blacklist(anyString(), any(Instant.class));
    }

    // ─── T-AUTH-04 residuo: changePassword ───────────────────────────────────

    @Test
    void changePasswordSuccessIncreasesVersionAndIssuesNewTokenPair() {
        final String newRawPassword = NEW_PASSWORD;
        final String newHash = "newhashedpassword";
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(passwordEncoder.encode(newRawPassword)).thenReturn(newHash);
        when(jwtService.generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_TOKEN);
        when(jwtService.generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), anyBoolean()))
                .thenReturn(MOCK_REFRESH_TOKEN);

        final AuthResponse response = authService.changePassword(TEST_USER,
                new ChangePasswordRequest(RAW_PASSWORD, newRawPassword));

        assertNotNull(response, "Response must not be null");
        assertEquals(MOCK_TOKEN, response.token());
        assertEquals(MOCK_REFRESH_TOKEN, response.refreshToken());
        assertEquals(1, testUser.getTokenVersion(),
                "tokenVersion must be incremented to 1 after password change");
        assertEquals(newHash, testUser.getPasswordHash(),
                "Password hash must be updated to the new value");
        verify(userRepository).save(Objects.requireNonNull(testUser));
        verify(refreshTokenService).storeTokenVersion(eq(TEST_USER), eq(1), any(Duration.class));
        verify(jwtService).generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), eq(false));
        verify(jwtService).generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), eq(false));
    }

    @Test
    void changePasswordClearsMustChangePasswordEvenWhenItWasTrue() {
        final UserAccount userWithTempPassword = UserAccount.builder()
                .username(TEST_USER)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.RECEPTIONIST)
                .hotelId(TEST_HOTEL_ID)
                .active(true)
                .mustChangePassword(true)
                .build();
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(userWithTempPassword));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("newhashedpassword");
        when(jwtService.generateToken(anyString(), any(Role.class), any(UUID.class), anyInt(), eq(false)))
                .thenReturn(MOCK_TOKEN);
        when(jwtService.generateRefreshToken(anyString(), any(Role.class), any(UUID.class), anyInt(), eq(false)))
                .thenReturn(MOCK_REFRESH_TOKEN);

        final AuthResponse response = authService.changePassword(TEST_USER,
                new ChangePasswordRequest(RAW_PASSWORD, NEW_PASSWORD));

        assertEquals(MOCK_TOKEN, response.token(),
                "BUG-5: the fresh token pair issued after a password change must not carry "
                        + "mustChangePassword=true, otherwise the gateway would keep blocking the user");
        assertFalse(response.mustChangePassword());
    }

    @Test
    void changePasswordThrowsWhenCurrentPasswordIsWrong() {
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(WRONG_PASSWORD, HASHED_PASSWORD)).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> authService.changePassword(TEST_USER,
                        new ChangePasswordRequest(WRONG_PASSWORD, NEW_PASSWORD)),
                "Should reject password change when current password does not match");
        verify(userRepository).findByUsername(TEST_USER);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void changePasswordThrowsWhenUserNotFound() {
        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authService.changePassword(TEST_USER,
                        new ChangePasswordRequest(RAW_PASSWORD, NEW_PASSWORD)),
                "Should reject password change for non-existent users");
    }

    @Test
    void invalidateRefreshTokenBlacklistsValidToken() {
        when(jwtService.extractJti(MOCK_REFRESH_TOKEN)).thenReturn(TEST_JTI);
        when(jwtService.extractExpirationInstant(MOCK_REFRESH_TOKEN))
                .thenReturn(Instant.now().plusSeconds(FUTURE_TTL_SECONDS));

        authService.invalidateRefreshToken(MOCK_REFRESH_TOKEN);

        verify(refreshTokenService).blacklist(eq(TEST_JTI), any(Instant.class));
    }

    @Test
    void invalidateRefreshTokenSilentlyHandlesInvalidToken() {
        when(jwtService.extractJti(MOCK_REFRESH_TOKEN))
                .thenThrow(new JwtException("invalid token"));

        assertDoesNotThrow(() -> authService.invalidateRefreshToken(MOCK_REFRESH_TOKEN),
                "invalidateRefreshToken must not propagate JwtException");
        verify(refreshTokenService, never()).blacklist(anyString(), any(Instant.class));
    }
}
