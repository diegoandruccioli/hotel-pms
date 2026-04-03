package com.hotelpms.auth.dto;

import com.hotelpms.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for user registration requests.
 *
 * @param username the chosen username
 * @param password the chosen password (min 8 chars)
 * @param email    the user's email address
 * @param role     the role assigned to the user
 */
public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password,
        @NotBlank @Email String email,
        @NotNull Role role) {
}
