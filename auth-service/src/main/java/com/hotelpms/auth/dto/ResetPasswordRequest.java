package com.hotelpms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload for an ADMIN or OWNER resetting another user's password.
 * The hotelId is taken from the gateway-injected header, not from this DTO.
 *
 * @param newPassword the new password to set (same complexity as CreateUserRequest)
 */
public record ResetPasswordRequest(
        @NotBlank @Pattern(
                regexp = "^(?=.*[A-Z].*[A-Z])(?=.*[0-9].*[0-9])(?=.*[^A-Za-z0-9].*[^A-Za-z0-9]).{16,}$",
                message = "PASSWORD_TOO_WEAK") String newPassword) {
}
