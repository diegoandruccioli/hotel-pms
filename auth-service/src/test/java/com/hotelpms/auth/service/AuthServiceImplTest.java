package com.hotelpms.auth.service;

import com.hotelpms.auth.domain.Role;
import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.LoginRequest;
import com.hotelpms.auth.dto.RegisterRequest;
import com.hotelpms.auth.exception.AccountLockedException;
import com.hotelpms.auth.exception.BadCredentialsException;
import com.hotelpms.auth.exception.DuplicateResourceException;
import com.hotelpms.auth.mapper.UserAccountMapper;
import com.hotelpms.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private UserAccountMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private UserAccount testUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = UserAccount.builder()
                .username(TEST_USER)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.GUEST)
                .active(true)
                .build();

        registerRequest = new RegisterRequest(TEST_USER, RAW_PASSWORD, TEST_EMAIL, Role.GUEST);
        loginRequest = new LoginRequest(TEST_USER, RAW_PASSWORD);
    }

    @Test
    void registerSuccess() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userMapper.toEntity(any(RegisterRequest.class))).thenReturn(testUser);
        when(passwordEncoder.encode(anyString())).thenReturn(HASHED_PASSWORD);
        when(jwtService.generateToken(anyString(), any(Role.class))).thenReturn(MOCK_TOKEN);

        final AuthResponse response = authService.register(registerRequest);

        assertNotNull(response, "Response should not be null");
        assertEquals(MOCK_TOKEN, response.token(), "Token should match the mocked one");

        verify(userRepository).save(Objects.requireNonNull(testUser));
        verify(passwordEncoder).encode(RAW_PASSWORD);
    }

    @Test
    void registerThrowsWhenUsernameExists() {
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest),
                "Should throw DuplicateResourceException when username is taken");

        verify(userRepository).existsByUsername(registerRequest.username());
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void registerThrowsWhenEmailExists() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest),
                "Should throw DuplicateResourceException when email is taken");

        verify(userRepository).existsByUsername(registerRequest.username());
        verify(userRepository).existsByEmail(registerRequest.email());
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void loginSuccess() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(testUser.getUsername(), testUser.getRole())).thenReturn(MOCK_TOKEN);

        final AuthResponse response = authService.login(loginRequest);

        assertNotNull(response, "Response should not be null");
        assertEquals(MOCK_TOKEN, response.token(), "Token should match mocked one");
    }

    @Test
    void loginThrowsWhenUserNotFound() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest),
                "Should throw BadCredentialsException when user is not found");
    }

    @Test
    void loginThrowsWhenPasswordMismatch() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest),
                "Should throw BadCredentialsException when password does not match");
    }

    @Test
    void loginThrowsWhenAccountIsLocked() {
        final UserAccount lockedUser = UserAccount.builder()
                .username(TEST_USER)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.GUEST)
                .active(true)
                .failedAttempts(MAX_FAILED_ATTEMPTS)
                .lockedUntil(Instant.now().plus(Duration.ofMinutes(10)))
                .build();

        when(userRepository.findByUsername(TEST_USER)).thenReturn(Optional.of(lockedUser));

        assertThrows(AccountLockedException.class, () -> authService.login(loginRequest),
                "Should throw AccountLockedException when account is locked");

        verifyNoMoreInteractions(passwordEncoder);
    }

    @Test
    void loginIncrementsFailedAttemptsOnWrongPassword() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));

        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getFailedAttempts(),
                "Failed attempts counter should be incremented to 1");
        assertNull(captor.getValue().getLockedUntil(),
                "Account should not be locked after a single failure");
    }

    @Test
    void loginLocksAccountAfterMaxFailedAttempts() {
        final UserAccount nearLockUser = UserAccount.builder()
                .username(TEST_USER)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.GUEST)
                .active(true)
                .failedAttempts(4)
                .build();

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(nearLockUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));

        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(captor.capture());
        assertEquals(MAX_FAILED_ATTEMPTS, captor.getValue().getFailedAttempts(),
                "Failed attempts counter should reach MAX_FAILED_ATTEMPTS");
        assertNotNull(captor.getValue().getLockedUntil(),
                "Account must be locked after reaching MAX_FAILED_ATTEMPTS");
    }

    @Test
    void loginResetsCounterOnSuccess() {
        final UserAccount userWithPriorFailures = UserAccount.builder()
                .username(TEST_USER)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASSWORD)
                .role(Role.GUEST)
                .active(true)
                .failedAttempts(3)
                .build();

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(userWithPriorFailures));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(TEST_USER, Role.GUEST)).thenReturn(MOCK_TOKEN);

        final AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals(MOCK_TOKEN, response.token());

        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getFailedAttempts(),
                "Failed attempts counter must be reset to 0 on successful login");
        assertNull(captor.getValue().getLockedUntil(),
                "Locked-until must be cleared on successful login");
    }
}
