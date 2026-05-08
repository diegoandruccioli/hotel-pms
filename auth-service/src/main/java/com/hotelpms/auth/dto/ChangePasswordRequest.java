package com.hotelpms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for password change requests.
 *
 * <p>The caller must supply the current password to prevent an attacker with a
 * stolen access token from silently replacing the victim's credentials.</p>
 *
 * @param currentPassword the user's existing password for identity re-verification
 * @param newPassword     the replacement password (minimum 8 characters)
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword) {
}
