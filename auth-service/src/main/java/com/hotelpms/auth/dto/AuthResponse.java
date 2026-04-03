package com.hotelpms.auth.dto;

/**
 * Data Transfer Object for authentication responses.
 *
 * @param token the generated JWT string
 */
public record AuthResponse(
        String token) {
}
