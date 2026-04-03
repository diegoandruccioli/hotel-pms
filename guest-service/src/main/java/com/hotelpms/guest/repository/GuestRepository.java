package com.hotelpms.guest.repository;

import com.hotelpms.guest.model.Guest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Guest entity.
 */
@Repository
public interface GuestRepository extends JpaRepository<Guest, UUID> {

    /**
     * Finds a guest by email.
     *
     * @param email the guest's email
     * @return the guest if found
     */
    Optional<Guest> findByEmail(String email);

    /**
     * Checks if a guest exists by email.
     *
     * @param email the guest's email
     * @return true if exists
     */
    boolean existsByEmail(String email);

    /**
     * Searches guests by first name, last name, email or city containing the
     * provided value (case-insensitive).
     *
     * @param keyword  search term matched against first name, last name, email, city
     * @param pageable pagination information
     * @return a page of matching guests
     */
    @Query("SELECT g FROM Guest g WHERE "
            + "LOWER(g.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(g.lastName)  LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(g.email)     LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + "LOWER(g.city)      LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Guest> searchByKeyword(
            @Param("keyword") String keyword,
            Pageable pageable);
}
