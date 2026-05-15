package com.hotelpms.auth.service;

import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.dto.CreateUserRequest;
import com.hotelpms.auth.dto.UserResponse;
import com.hotelpms.auth.exception.DuplicateResourceException;
import com.hotelpms.auth.exception.NotFoundException;
import com.hotelpms.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link UserManagementService}.
 */
@SuppressWarnings("null")
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final String MSG_USER_NOT_FOUND = "USER_NOT_FOUND";

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(final UUID hotelId) {
        return userRepository.findAllByHotelId(hotelId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public UserResponse createUser(final UUID hotelId, final CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("USERNAME_ALREADY_EXISTS");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("EMAIL_ALREADY_EXISTS");
        }

        final UserAccount user = UserAccount.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .email(request.email())
                .role(request.role())
                .hotelId(hotelId)
                .mustChangePassword(true)
                .active(true)
                .build();

        userRepository.save(user);
        log.info("[AUTH] USER_CREATED | username={} | role={} | hotelId={}", user.getUsername(), user.getRole(), hotelId);
        return toResponse(user);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public UserResponse deactivateUser(final UUID hotelId, final UUID targetUserId,
            final String requestingUser) {
        final UserAccount target = userRepository.findByIdAndHotelId(targetUserId, hotelId)
                .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        if (target.getUsername().equals(requestingUser)) {
            throw new IllegalStateException("CANNOT_DEACTIVATE_SELF");
        }

        target.setActive(false);
        userRepository.save(target);
        log.info("[AUTH] USER_DEACTIVATED | userId={} | by={}", targetUserId, requestingUser);
        return toResponse(target);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public UserResponse activateUser(final UUID hotelId, final UUID targetUserId) {
        // Must query including inactive — use raw query bypassing @SQLRestriction
        final UserAccount target = userRepository.findById(targetUserId)
                .filter(u -> hotelId.equals(u.getHotelId()))
                .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        target.setActive(true);
        userRepository.save(target);
        log.info("[AUTH] USER_ACTIVATED | userId={}", targetUserId);
        return toResponse(target);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void resetPassword(final UUID hotelId, final UUID targetUserId, final String newPassword) {
        final UserAccount target = userRepository.findByIdAndHotelId(targetUserId, hotelId)
                .orElseThrow(() -> new NotFoundException(MSG_USER_NOT_FOUND));

        target.setPasswordHash(passwordEncoder.encode(newPassword));
        target.setMustChangePassword(true);
        target.setTokenVersion(target.getTokenVersion() + 1);
        userRepository.save(target);

        refreshTokenService.storeTokenVersion(target.getUsername(), target.getTokenVersion(),
                REFRESH_TOKEN_TTL);

        log.info("[AUTH] PASSWORD_RESET | userId={} | newTokenVersion={}", targetUserId,
                target.getTokenVersion());
    }

    private UserResponse toResponse(final UserAccount u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getRole().name(),
                u.isActive(),
                u.isMustChangePassword(),
                u.getCreatedAt());
    }
}
