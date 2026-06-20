package com.hotelpms.auth.controller;

import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.ChangePasswordRequest;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
    /**
     * Non-httpOnly cookie carrying the CSRF synchronization token (T-GW-05).
     * JavaScript must be able to read this value to echo it in the X-CSRF-Token header.
     * Lifetime tracks {@code REFRESH_COOKIE_MAX_AGE} (the real session boundary), not
     * {@code ACCESS_COOKIE_MAX_AGE}: the double-submit-cookie pattern's security
     * property is unpredictability, not freshness, so there is no security reason to
     * expire it every 15 minutes — doing so only forced spurious re-logins on any
     * sustained workflow that outlived one access-token cycle.
     */
    private static final String CSRF_COOKIE_NAME = "csrf_token";
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
    private final com.hotelpms.auth.repository.UserAccountRepository userRepository;

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
                .header(HttpHeaders.SET_COOKIE,
                        createCsrfCookie(UUID.randomUUID().toString(), REFRESH_COOKIE_MAX_AGE).toString())
                .build();
    }

    /**
     * Endpoint for user login.
     * Returns a body with {@code mustChangePassword} so the SPA can immediately
     * redirect to the change-password page when the flag is set.
     *
     * @param request the login request
     * @return HTTP 200 with access and refresh cookies and a JSON body
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@NonNull final @Valid @RequestBody LoginRequest request) {
        final AuthResponse response = authService.login(request);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("mustChangePassword", response.mustChangePassword());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(COOKIE_NAME, response.token(), ACCESS_COOKIE_MAX_AGE, COOKIE_PATH).toString())
                .header(HttpHeaders.SET_COOKIE,
                        createCookie(REFRESH_COOKIE_NAME, response.refreshToken(),
                                REFRESH_COOKIE_MAX_AGE, REFRESH_COOKIE_PATH).toString())
                .header(HttpHeaders.SET_COOKIE,
                        createCsrfCookie(UUID.randomUUID().toString(), REFRESH_COOKIE_MAX_AGE).toString())
                .body(body);
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
                    .header(HttpHeaders.SET_COOKIE,
                            createCsrfCookie(UUID.randomUUID().toString(), REFRESH_COOKIE_MAX_AGE).toString())
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
                .header(HttpHeaders.SET_COOKIE,
                        createCsrfCookie("", 0).toString())
                .build();
    }

    /**
     * Endpoint for changing the authenticated user's password (T-AUTH-04 residuo).
     *
     * <p>Requires the current password as a second-factor check: an attacker who
     * obtained a valid access token cannot silently replace the victim's credentials.
     * On success, {@code tokenVersion} is incremented, all pre-existing sessions are
     * invalidated, and fresh cookies are set so the requesting session remains active.</p>
     *
     * @param token   the access JWT cookie; {@code null} if absent
     * @param request the change-password payload (current + new password)
     * @return HTTP 200 with refreshed cookies on success,
     *         HTTP 401 when the JWT cookie is missing, invalid, or expired
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @CookieValue(name = COOKIE_NAME, required = false) final String token,
            @NonNull final @Valid @RequestBody ChangePasswordRequest request) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            final String username = jwtService.extractUsername(token);
            if (!jwtService.isTokenValid(token, username)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            final AuthResponse response = authService.changePassword(username, request);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE,
                            createCookie(COOKIE_NAME, response.token(),
                                    ACCESS_COOKIE_MAX_AGE, COOKIE_PATH).toString())
                    .header(HttpHeaders.SET_COOKIE,
                            createCookie(REFRESH_COOKIE_NAME, response.refreshToken(),
                                    REFRESH_COOKIE_MAX_AGE, REFRESH_COOKIE_PATH).toString())
                    .header(HttpHeaders.SET_COOKIE,
                            createCsrfCookie(UUID.randomUUID().toString(),
                                    REFRESH_COOKIE_MAX_AGE).toString())
                    .build();
        } catch (final JwtException | IllegalArgumentException e) {
            log.warn("[AUTH] CHANGE_PASSWORD_REJECTED | reason=INVALID_JWT | detail={}",
                    e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Endpoint for checking the current authenticated user.
     *
     * @param token the access JWT cookie
     * @return the user payload, or HTTP 401 if the token is absent or invalid
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(
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

            final boolean mustChange = userRepository.findByUsername(username)
                    .map(com.hotelpms.auth.domain.UserAccount::isMustChangePassword)
                    .orElse(false);
            final Map<String, Object> meBody = new LinkedHashMap<>();
            meBody.put("sub", username);
            meBody.put("username", username);
            meBody.put("role", role);
            meBody.put("mustChangePassword", mustChange);
            return ResponseEntity.ok(meBody);
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

    /**
     * Creates the CSRF synchronization cookie (T-GW-05 — Double Submit Cookie pattern).
     *
     * <p>Unlike the JWT and refresh cookies, this cookie is intentionally
     * <em>not</em> httpOnly so that the SPA JavaScript can read its value and
     * echo it in the {@code X-CSRF-Token} request header. The API Gateway
     * ({@link com.hotelpms.gateway.filter.CsrfFilter}) then validates that the
     * header matches the cookie, defeating cross-site request forgery: a foreign
     * origin cannot read the cookie value (same-origin policy), therefore it
     * cannot forge the matching header.
     *
     * @param value  the CSRF token (UUID); empty string to clear the cookie
     * @param maxAge cookie lifetime in seconds; 0 to expire immediately
     * @return the built {@link ResponseCookie}
     */
    private static ResponseCookie createCsrfCookie(final String value, final int maxAge) {
        return ResponseCookie.from(
                CSRF_COOKIE_NAME,
                Objects.requireNonNull(value != null ? value : ""))
                .httpOnly(false)
                .secure(true)
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .sameSite(SAME_SITE_STRICT)
                .build();
    }
}
