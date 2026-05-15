package com.hotelpms.auth.service;

import com.hotelpms.auth.domain.Role;
import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.dto.CreateUserRequest;
import com.hotelpms.auth.dto.UserResponse;
import com.hotelpms.auth.exception.DuplicateResourceException;
import com.hotelpms.auth.exception.NotFoundException;
import com.hotelpms.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HOTEL_OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String USERNAME = "newuser";
    private static final String PASSWORD = "password123";
    private static final String HASHED_PW = "hashed_password";
    private static final String EMAIL = "user@example.com";
    private static final String REQUESTING_ADMIN = "adminuser";

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserManagementServiceImpl userManagementService;

    private UserAccount activeUser;
    private UserAccount inactiveUser;

    @BeforeEach
    void setUp() {
        activeUser = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .passwordHash(HASHED_PW)
                .email(EMAIL)
                .role(Role.RECEPTIONIST)
                .hotelId(HOTEL_ID)
                .mustChangePassword(true)
                .active(true)
                .build();

        inactiveUser = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .passwordHash(HASHED_PW)
                .email(EMAIL)
                .role(Role.RECEPTIONIST)
                .hotelId(HOTEL_ID)
                .mustChangePassword(false)
                .active(false)
                .build();
    }

    @Test
    void listUsersShouldReturnMappedResponse() {
        when(userRepository.findAllByHotelId(HOTEL_ID)).thenReturn(List.of(activeUser));

        final List<UserResponse> result = userManagementService.listUsers(HOTEL_ID);

        assertEquals(1, result.size());
        assertEquals(USERNAME, result.get(0).username());
        assertEquals(EMAIL, result.get(0).email());
        assertTrue(result.get(0).mustChangePassword());
        verify(userRepository).findAllByHotelId(HOTEL_ID);
    }

    @Test
    void listUsersShouldReturnEmptyListWhenNoUsers() {
        when(userRepository.findAllByHotelId(HOTEL_ID)).thenReturn(List.of());

        final List<UserResponse> result = userManagementService.listUsers(HOTEL_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void createUserShouldPersistAndReturnMustChangePasswordTrue() {
        final CreateUserRequest request = new CreateUserRequest(USERNAME, PASSWORD, EMAIL, Role.RECEPTIONIST);
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PW);
        when(userRepository.save(any(UserAccount.class))).thenReturn(activeUser);

        final UserResponse result = userManagementService.createUser(HOTEL_ID, request);

        assertEquals(USERNAME, result.username());
        assertTrue(result.mustChangePassword());
        verify(passwordEncoder).encode(PASSWORD);
        verify(userRepository).save(any(UserAccount.class));
    }

    @Test
    void createUserShouldThrowWhenUsernameAlreadyExists() {
        final CreateUserRequest request = new CreateUserRequest(USERNAME, PASSWORD, EMAIL, Role.RECEPTIONIST);
        when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> userManagementService.createUser(HOTEL_ID, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserShouldThrowWhenEmailAlreadyExists() {
        final CreateUserRequest request = new CreateUserRequest(USERNAME, PASSWORD, EMAIL, Role.RECEPTIONIST);
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> userManagementService.createUser(HOTEL_ID, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUserShouldSetActiveFalse() {
        when(userRepository.findByIdAndHotelId(USER_ID, HOTEL_ID)).thenReturn(Optional.of(activeUser));

        final UserResponse result = userManagementService.deactivateUser(HOTEL_ID, USER_ID, REQUESTING_ADMIN);

        assertFalse(activeUser.isActive());
        assertFalse(result.active());
        verify(userRepository).save(activeUser);
    }

    @Test
    void deactivateUserShouldThrowWhenNotFound() {
        when(userRepository.findByIdAndHotelId(USER_ID, HOTEL_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> userManagementService.deactivateUser(HOTEL_ID, USER_ID, REQUESTING_ADMIN));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUserShouldThrowWhenRequestingUserIsTarget() {
        when(userRepository.findByIdAndHotelId(USER_ID, HOTEL_ID)).thenReturn(Optional.of(activeUser));

        assertThrows(IllegalStateException.class,
                () -> userManagementService.deactivateUser(HOTEL_ID, USER_ID, USERNAME));
        verify(userRepository, never()).save(any());
    }

    @Test
    void activateUserShouldSetActiveTrue() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(inactiveUser));

        final UserResponse result = userManagementService.activateUser(HOTEL_ID, USER_ID);

        assertTrue(inactiveUser.isActive());
        assertTrue(result.active());
        verify(userRepository).save(inactiveUser);
    }

    @Test
    void activateUserShouldThrowWhenNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> userManagementService.activateUser(HOTEL_ID, USER_ID));
    }

    @Test
    void activateUserShouldThrowWhenUserBelongsToAnotherHotel() {
        final UserAccount wrongHotelUser = UserAccount.builder()
                .id(USER_ID)
                .username(USERNAME)
                .hotelId(HOTEL_OTHER)
                .active(false)
                .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(wrongHotelUser));

        assertThrows(NotFoundException.class,
                () -> userManagementService.activateUser(HOTEL_ID, USER_ID));
        verify(userRepository, never()).save(any());
    }
}
