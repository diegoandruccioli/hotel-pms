package com.hotelpms.auth.controller;

import com.hotelpms.auth.dto.CreateUserRequest;
import com.hotelpms.auth.dto.UserResponse;
import com.hotelpms.auth.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for ADMIN/OWNER user management within a hotel.
 * All endpoints are scoped to the requesting admin's hotelId (from X-Auth-Hotel)
 * to prevent cross-tenant access.
 *
 * <p>Base path: {@code /api/v1/auth/users}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
public class UserManagementController {

    private static final String ROLE_ADMIN_OWNER = "hasAnyRole('ADMIN', 'OWNER')";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";

    private final UserManagementService userManagementService;

    /**
     * Lists all active users for the requesting admin's hotel.
     *
     * @param hotelId the hotel UUID from the gateway-injected header
     * @return list of user summaries
     */
    @GetMapping
    @PreAuthorize(ROLE_ADMIN_OWNER)
    public ResponseEntity<List<UserResponse>> listUsers(
            @NonNull @RequestHeader(HEADER_HOTEL) final String hotelId) {
        return ResponseEntity.ok(userManagementService.listUsers(UUID.fromString(hotelId)));
    }

    /**
     * Creates a new user within the requesting admin's hotel.
     * The new account is flagged {@code mustChangePassword=true}.
     *
     * @param hotelId the hotel UUID from the gateway-injected header
     * @param request the creation payload
     * @return the created user (HTTP 201)
     */
    @PostMapping
    @PreAuthorize(ROLE_ADMIN_OWNER)
    public ResponseEntity<UserResponse> createUser(
            @NonNull @RequestHeader(HEADER_HOTEL) final String hotelId,
            @NonNull @Valid @RequestBody final CreateUserRequest request) {
        final UserResponse created = userManagementService.createUser(UUID.fromString(hotelId), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Deactivates (soft-deletes) a user account.
     *
     * @param hotelId    the hotel UUID from the gateway-injected header
     * @param userId     the target user's UUID
     * @param auth       the Spring Security authentication (contains the requesting username)
     * @return the updated user summary
     */
    @PatchMapping("/{userId}/deactivate")
    @PreAuthorize(ROLE_ADMIN_OWNER)
    public ResponseEntity<UserResponse> deactivateUser(
            @NonNull @RequestHeader(HEADER_HOTEL) final String hotelId,
            @NonNull @PathVariable final UUID userId,
            @NonNull final Authentication auth) {
        return ResponseEntity.ok(
                userManagementService.deactivateUser(UUID.fromString(hotelId), userId, auth.getName()));
    }

    /**
     * Reactivates a previously deactivated user account.
     *
     * @param hotelId the hotel UUID from the gateway-injected header
     * @param userId  the target user's UUID
     * @return the updated user summary
     */
    @PatchMapping("/{userId}/activate")
    @PreAuthorize(ROLE_ADMIN_OWNER)
    public ResponseEntity<UserResponse> activateUser(
            @NonNull @RequestHeader(HEADER_HOTEL) final String hotelId,
            @NonNull @PathVariable final UUID userId) {
        return ResponseEntity.ok(
                userManagementService.activateUser(UUID.fromString(hotelId), userId));
    }
}
