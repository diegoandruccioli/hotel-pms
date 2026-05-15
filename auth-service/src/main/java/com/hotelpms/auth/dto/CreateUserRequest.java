package com.hotelpms.auth.dto;

import com.hotelpms.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload for an ADMIN or OWNER creating a new user within their hotel.
 * The hotelId is taken from the authenticated request (X-Auth-Hotel), not from this DTO.
 *
 * @param username the new user's login handle
 * @param password the initial password (≥16 chars, 2 uppercase, 2 digits, 2 special)
 * @param email    the new user's email address
 * @param role     the role to assign (RECEPTIONIST, OWNER, etc.)
 */
public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Pattern(
                regexp = "^(?=.*[A-Z].*[A-Z])(?=.*[0-9].*[0-9])(?=.*[^A-Za-z0-9].*[^A-Za-z0-9]).{16,}$",
                message = "PASSWORD_TOO_WEAK") String password,
        @NotBlank @Email String email,
        @NotNull Role role) {
}
