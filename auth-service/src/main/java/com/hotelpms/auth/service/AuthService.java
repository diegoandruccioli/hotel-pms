package com.hotelpms.auth.service;

import com.hotelpms.auth.dto.AuthResponse;
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
     * @return an {@link AuthResponse} containing the generated JWT token
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates a user and returns a JWT token.
     *
     * @param request the login request containing username and password
     * @return an {@link AuthResponse} containing the generated JWT token
     */
    AuthResponse login(LoginRequest request);
}
