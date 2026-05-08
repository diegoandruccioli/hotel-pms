package com.hotelpms.auth.dto;

/**
 * Data Transfer Object for authentication responses.
 *
 * @param token              the short-lived access JWT
 * @param refreshToken       the long-lived refresh JWT (rotated on each use — T-AUTH-04)
 * @param mustChangePassword when {@code true} the UI must redirect to the change-password
 *                           page before allowing any other navigation
 */
public record AuthResponse(
        String token,
        String refreshToken,
        boolean mustChangePassword) {
}
