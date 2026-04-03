package com.hotelpms.auth.controller;

import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.LoginRequest;
import com.hotelpms.auth.dto.RegisterRequest;
import com.hotelpms.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import com.hotelpms.auth.service.JwtService;
import io.jsonwebtoken.JwtException;
import java.util.Map;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String COOKIE_NAME = "jwt";
    private static final String COOKIE_PATH = "/";
    private static final String SAME_SITE_STRICT = "Strict";
    private static final int COOKIE_MAX_AGE = 86_400;

    private final AuthService authService;
    private final JwtService jwtService;

    /**
     * Endpoint for user registration.
     *
     * @param request the registration request
     * @return the authentication response containing the JWT
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@NonNull final @Valid @RequestBody RegisterRequest request) {
        final AuthResponse response = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, createCookie(response.token(), COOKIE_MAX_AGE).toString())
                .build();
    }

    /**
     * Endpoint for user login.
     *
     * @param request the login request
     * @return the authentication response containing the JWT
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@NonNull final @Valid @RequestBody LoginRequest request) {
        final AuthResponse response = authService.login(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createCookie(response.token(), COOKIE_MAX_AGE).toString())
                .build();
    }

    /**
     * Endpoint for user logout.
     *
     * @return the empty response with cleared cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createCookie("", 0).toString())
                .build();
    }

    private ResponseCookie createCookie(final String value, final int maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value != null ? value : "")
                .httpOnly(true)
                .secure(true)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .sameSite(SAME_SITE_STRICT)
                .build();
    }

    /**
     * Endpoint for checking current authenticated user.
     *
     * @param token the JWT cookie
     * @return the user payload
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getMe(@CookieValue(name = "jwt", required = false) final String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            final String username = jwtService.extractUsername(token);
            final String role = jwtService.extractClaim(token, claims -> claims.get("role", String.class));

            // if we need to call isTokenValid, we need username first, or simply check
            // expiration
            if (!jwtService.isTokenValid(token, username)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            return ResponseEntity.ok(Map.of(
                    "sub", username,
                    "username", username,
                    "role", role));
        } catch (final JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
