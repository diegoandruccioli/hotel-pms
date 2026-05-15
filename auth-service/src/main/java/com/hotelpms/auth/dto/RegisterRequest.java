package com.hotelpms.auth.dto;

import com.hotelpms.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Data Transfer Object for user registration requests.
 *
 * @param username the chosen username
 * @param password the chosen password (≥16 chars, 2 uppercase, 2 digits, 2 special)
 * @param email    the user's email address
 * @param role     the role assigned to the user
 * @param hotelId  the tenant identifier for multi-hotel isolation
 */
public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Pattern(
                regexp = "^(?=.*[A-Z].*[A-Z])(?=.*[0-9].*[0-9])(?=.*[^A-Za-z0-9].*[^A-Za-z0-9]).{16,}$",
                message = "PASSWORD_TOO_WEAK") String password,
        @NotBlank @Email String email,
        @NotNull Role role,
        @NotNull UUID hotelId) {
}
