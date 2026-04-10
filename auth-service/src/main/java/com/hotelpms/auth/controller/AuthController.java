package com.hotelpms.auth.controller;

import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.LoginRequest;
import com.hotelpms.auth.dto.RegisterRequest;
import com.hotelpms.auth.exception.BadCredentialsException;
import com.hotelpms.auth.service.AuthService;
import com.hotelpms.auth.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * REST controller for authentication endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String COOKIE_NAME = "jwt";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/";
    /** Refresh cookie is scoped to the auth path only (least-privilege exposure). */
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE_STRICT = "Strict";
    /** Access token cookie lifetime: 15 minutes (matches jwt.expiration). */
    private static final int ACCESS_COOKIE_MAX_AGE = 900;
    /** Refresh token cookie lifetime: 7 days (matches jwt.refresh-expiration). */
    private static final int REFRESH_COOKIE_MAX_AGE = 604_800;

    private final AuthService authService;
    private final JwtService jwtService;

    /**
     * Endpoint for user registration.
     *
     * @param request the registration request
     * @return HTTP 201 with access and refresh cookies
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@NonNull final @Valid @RequestBody RegisterRequest request) {
        final AuthResponse response = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(COOKIE_NAME, response.token(), ACCESS_COOKIE_MAX_AGE, COOKIE_PATH).toString())
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(REFRESH_COOKIE_NAME, response.refreshToken(),
                                REFRESH_COOKIE_MAX_AGE, REFRESH_COOKIE_PATH).toString())
                .build();
    }

    /**
     * Endpoint for user login.
     *
     * @param request the login request
     * @return HTTP 200 with access and refresh cookies
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@NonNull final @Valid @RequestBody LoginRequest request) {
        final AuthResponse response = authService.login(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(COOKIE_NAME, response.token(), ACCESS_COOKIE_MAX_AGE, COOKIE_PATH).toString())
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(REFRESH_COOKIE_NAME, response.refreshToken(),
                                REFRESH_COOKIE_MAX_AGE, REFRESH_COOKIE_PATH).toString())
                .build();
    }

    /**
     * Endpoint for silent token rotation (T-AUTH-04).
     *
     * <p>Validates the refresh token cookie, blacklists its JTI, and
     * issues a new access + refresh token pair in fresh cookies.</p>
     *
     * @param refreshToken the current refresh JWT from the cookie; may be absent
     * @return HTTP 200 with rotated cookies, or HTTP 401 with cleared cookies on failure
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) final String refreshToken) {
        if (refreshToken == null) {
            return buildUnauthorizedResponse();
        }
        try {
            final AuthResponse response = authService.refresh(refreshToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE,
                            createCookie(COOKIE_NAME, response.token(),
                                    ACCESS_COOKIE_MAX_AGE, COOKIE_PATH).toString())
                    .header(HttpHeaders.SET_COOKIE,
                            createCookie(REFRESH_COOKIE_NAME, response.refreshToken(),
                                    REFRESH_COOKIE_MAX_AGE, REFRESH_COOKIE_PATH).toString())
                    .build();
        } catch (final JwtException e) {
            log.warn("[AUTH] REFRESH_REJECTED | reason=INVALID_JWT | detail={}", e.getMessage());
            return buildUnauthorizedResponse();
        } catch (final BadCredentialsException e) {
            // Already logged in AuthServiceImpl with specific reason
            return buildUnauthorizedResponse();
        }
    }

    /**
     * Endpoint for user logout.
     *
     * <p>Blacklists the refresh token JTI so it cannot be reused (T-AUTH-04),
     * then clears both authentication cookies.</p>
     *
     * @param token        the access JWT cookie; may be absent if already expired
     * @param refreshToken the refresh JWT cookie; may be absent
     * @return HTTP 200 with cleared cookies
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = COOKIE_NAME, required = false) final String token,
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) final String refreshToken) {
        if (token != null) {
            try {
                final String username = jwtService.extractUsername(token);
                log.info("[AUTH] LOGOUT | user={}", username);
            } catch (final JwtException | IllegalArgumentException e) {
                log.info("[AUTH] LOGOUT | user=unknown (invalid token)");
            }
        }
        if (refreshToken != null) {
            authService.invalidateRefreshToken(refreshToken);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(COOKIE_NAME, "", 0, COOKIE_PATH).toString())
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(REFRESH_COOKIE_NAME, "", 0, REFRESH_COOKIE_PATH).toString())
                .build();
    }

    /**
     * Endpoint for checking the current authenticated user.
     *
     * @param token the access JWT cookie
     * @return the user payload, or HTTP 401 if the token is absent or invalid
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getMe(
            @CookieValue(name = "jwt", required = false) final String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            final String username = jwtService.extractUsername(token);
            final String role = jwtService.extractClaim(token, claims -> claims.get("role", String.class));

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

    private ResponseEntity<Void> buildUnauthorizedResponse() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(COOKIE_NAME, "", 0, COOKIE_PATH).toString())
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(REFRESH_COOKIE_NAME, "", 0, REFRESH_COOKIE_PATH).toString())
                .build();
    }

    private static ResponseCookie createCookie(final String name, final String value,
            final int maxAge, final String path) {
        return ResponseCookie.from(
                Objects.requireNonNull(name),
                Objects.requireNonNull(value != null ? value : ""))
                .httpOnly(true)
                .secure(true)
                .path(path)
                .maxAge(maxAge)
                .sameSite(SAME_SITE_STRICT)
                .build();
    }
}
