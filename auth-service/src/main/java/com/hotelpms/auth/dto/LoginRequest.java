package com.hotelpms.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for user login requests.
 *
 * @param username the username or email
 * @param password the user's password
 */
public record LoginRequest(
                @NotBlank(message = "Username cannot be blank") String username,

                @NotBlank(message = "Password cannot be blank") String password) {
}
