package com.hotelpms.auth.service;

import com.hotelpms.auth.domain.UserAccount;
import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.LoginRequest;
import com.hotelpms.auth.dto.RegisterRequest;
import com.hotelpms.auth.exception.BadCredentialsException;
import com.hotelpms.auth.exception.DuplicateResourceException;
import com.hotelpms.auth.mapper.UserAccountMapper;
import com.hotelpms.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AuthService}.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

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

        final String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token);
    }

    /**
     * Authenticates a user.
     *
     * @param request the login request
     * @return the auth response
     */
    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(final LoginRequest request) {
        final UserAccount user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("INVALID_CREDENTIALS"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("INVALID_CREDENTIALS");
        }

        final String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token);
    }
}
