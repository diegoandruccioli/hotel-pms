package com.hotelpms.guest.repository;

import com.hotelpms.guest.model.Guest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds the {@link Specification} backing {@link GuestRepository#searchByKeywordAndHotelId}
 * (BUG-2, {@code docs/LIVE_E2E_AUDIT_2026-07.md}).
 *
 * <p>A single LIKE against the whole keyword only matches within one field, so a query like
 * "Test Verifica" never matched a guest whose first name is "Test" and last name is
 * "Verifica" — neither field contains the full two-word string. Splitting the keyword into
 * whitespace-separated tokens and requiring every token to match at least one field (an AND
 * of per-token ORs) fixes "Nome Cognome"/"Cognome Nome" style searches while still matching
 * plain single-token queries exactly as before.
 */
final class GuestSearchSpecifications {

    private GuestSearchSpecifications() {
    }

    /**
     * Matches guests in the given hotel where every whitespace-separated token in
     * {@code keyword} appears (case-insensitive, substring) in at least one of first name,
     * last name, email or city.
     *
     * @param keyword the free-text search term, expected non-blank
     * @param hotelId the owning hotel UUID (tenant isolation)
     * @return the combined specification
     */
    static Specification<Guest> matchingAllTokens(final String keyword, final UUID hotelId) {
        final String[] tokens = keyword.trim().toLowerCase(Locale.ROOT).split("\\s+");
        return (root, query, cb) -> {
            final List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("hotelId"), hotelId));
            for (final String token : tokens) {
                final String pattern = "%" + token + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("city")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
