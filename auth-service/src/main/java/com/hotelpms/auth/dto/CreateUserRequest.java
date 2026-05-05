package com.hotelpms.auth.dto;

import com.hotelpms.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for an ADMIN or OWNER creating a new user within their hotel.
 * The hotelId is taken from the authenticated request (X-Auth-Hotel), not from this DTO.
 *
 * @param username the new user's login handle
 * @param password the initial password (min 8 chars); the user will be prompted to change it
 * @param email    the new user's email address
 * @param role     the role to assign (RECEPTIONIST, OWNER, etc.)
 */
public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password,
        @NotBlank @Email String email,
        @NotNull Role role) {
}
