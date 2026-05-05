package com.hotelpms.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only projection of a {@link com.hotelpms.auth.domain.UserAccount}.
 * Returned by the user-management endpoints.
 *
 * @param id                the user UUID
 * @param username          the login username
 * @param email             the email address
 * @param role              the assigned role
 * @param active            whether the account is active
 * @param mustChangePassword whether the user must change their password on next login
 * @param createdAt         account creation timestamp
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String role,
        boolean active,
        boolean mustChangePassword,
        LocalDateTime createdAt) {
}
