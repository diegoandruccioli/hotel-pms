package com.hotelpms.auth.service;

import com.hotelpms.auth.dto.CreateUserRequest;
import com.hotelpms.auth.dto.UserResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service for ADMIN/OWNER operations on user accounts within a hotel.
 */
public interface UserManagementService {

    /**
     * Returns all active user accounts for the given hotel.
     *
     * @param hotelId the requesting admin's hotel
     * @return list of user summaries
     */
    List<UserResponse> listUsers(UUID hotelId);

    /**
     * Creates a new user account within the requesting admin's hotel.
     * The new user is flagged {@code mustChangePassword=true} so they are prompted
     * to choose a personal password on first login.
     *
     * @param hotelId the requesting admin's hotel
     * @param request the creation payload
     * @return the created user summary
     */
    UserResponse createUser(UUID hotelId, CreateUserRequest request);

    /**
     * Soft-deactivates a user account (sets {@code active=false}).
     * Cannot deactivate yourself or the last ADMIN account.
     *
     * @param hotelId       the requesting admin's hotel
     * @param targetUserId  the user to deactivate
     * @param requestingUser the username of the admin performing the action
     * @return the updated user summary
     */
    UserResponse deactivateUser(UUID hotelId, UUID targetUserId, String requestingUser);

    /**
     * Reactivates a previously deactivated user account.
     *
     * @param hotelId      the requesting admin's hotel
     * @param targetUserId the user to reactivate
     * @return the updated user summary
     */
    UserResponse activateUser(UUID hotelId, UUID targetUserId);
}
