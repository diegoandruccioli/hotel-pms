package com.hotelpms.auth.repository;

import com.hotelpms.auth.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
     * 
     * @return true if an account with the specified username exists, false
     *         otherwise
     */
    boolean existsByUsername(String username);
}
