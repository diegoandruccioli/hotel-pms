package com.hotelpms.auth.dto;

/**
 * Data Transfer Object for authentication responses.
 *
 * @param token        the short-lived access JWT
 * @param refreshToken the long-lived refresh JWT (rotated on each use — T-AUTH-04)
 */
public record AuthResponse(
        String token,
        String refreshToken) {
}
