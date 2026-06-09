package com.hotelpms.auth.repository;

import com.hotelpms.auth.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link UserAccount} entity.
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /**
     * Finds a user account by its username.
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the user account if found, empty
     *         otherwise
     */
    Optional<UserAccount> findByUsername(String username);

    /**
     * Checks if a user account exists by its email.
     *
     * @param email the email to check
     * @return true if an account with the specified email exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Checks if a user account exists by its username.
     *
     * @param username the username to check
     * @return true if an account with the specified username exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Returns all active users belonging to the given hotel.
     *
     * @param hotelId the hotel UUID
     * @return list of active user accounts for the hotel
     */
    List<UserAccount> findAllByHotelId(UUID hotelId);

    /**
     * Finds an active user by id scoped to a hotel (prevents cross-tenant access).
     *
     * @param id      the user UUID
     * @param hotelId the hotel UUID
     * @return the user if found and active within that hotel
     */
    Optional<UserAccount> findByIdAndHotelId(UUID id, UUID hotelId);

    /**
     * Atomically increments the failed-login counter and sets the lock expiry.
     * Runs in its own transaction so the update is committed even when the caller
     * rolls back (e.g. after throwing BadCredentialsException).
     *
     * @param username   the account username
     * @param attempts   the new failed-attempts value
     * @param lockedUntil the lock expiry (null = not yet locked)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Modifying
    @Query("UPDATE UserAccount u SET u.failedAttempts = :attempts, u.lockedUntil = :lockedUntil WHERE u.username = :username")
    void updateFailedAttempts(@Param("username") String username,
                              @Param("attempts") int attempts,
                              @Param("lockedUntil") Instant lockedUntil);
}
