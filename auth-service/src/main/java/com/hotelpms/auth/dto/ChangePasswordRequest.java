package com.hotelpms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Data Transfer Object for password change requests.
 *
 * <p>The caller must supply the current password to prevent an attacker with a
 * stolen access token from silently replacing the victim's credentials.</p>
 *
 * @param currentPassword the user's existing password for identity re-verification
 * @param newPassword     the replacement password (≥16 chars, 2 uppercase, 2 digits, 2 special)
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Pattern(
                regexp = "^(?=.*[A-Z].*[A-Z])(?=.*[0-9].*[0-9])(?=.*[^A-Za-z0-9].*[^A-Za-z0-9]).{16,}$",
                message = "PASSWORD_TOO_WEAK") String newPassword) {
}
