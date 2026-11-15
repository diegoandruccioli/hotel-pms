package com.hotelpms.frontdesk.quotations.repository;

import com.hotelpms.frontdesk.quotations.domain.Quotation;
import com.hotelpms.frontdesk.quotations.domain.QuotationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Quotation.
 */
public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

    /**
     * Lists every active quotation for a hotel, newest first.
     *
     * @param hotelId  the hotel UUID (multi-tenant scoping)
     * @param pageable pagination and sorting
     * @return a page of quotations
     */
    Page<Quotation> findAllByHotelId(@Param("hotelId") @NonNull UUID hotelId, @NonNull Pageable pageable);

    /**
     * Combinable search over a hotel's quotations: stored statuses, an optional {@code validUntil}
     * window and an optional free-text query. {@code EXPIRED} is not stored, so the service turns it
     * into the {@code DRAFT}/{@code SENT} statuses plus {@code validBefore}; a live {@code DRAFT}/
     * {@code SENT} filter passes {@code validFrom}. Any bound left {@code null} is skipped.
     *
     * <p>The query matches a prospect's name or email here and a registered guest through
     * {@code guestIds}, which the service resolves in guest-service (a quotation stores only the guest id).
     *
     * @param hotelId     the hotel UUID (multi-tenant scoping, always applied)
     * @param statuses    stored statuses to match; never empty
     * @param validFrom   when set, only quotations valid until this date or later
     * @param validBefore when set, only quotations valid until a date strictly before this one
     * @param query       optional free-text query, or {@code null} to skip it
     * @param guestIds    guest IDs matching {@code query}; non-null (empty when there is no query)
     * @param pageable    pagination and sorting parameters
     * @return a page of matching quotations
     */
    @Query("SELECT q FROM Quotation q WHERE q.hotelId = :hotelId "
            + "AND q.status IN :statuses "
            + "AND (CAST(:validFrom AS date) IS NULL OR q.validUntil >= :validFrom) "
            + "AND (CAST(:validBefore AS date) IS NULL OR q.validUntil < :validBefore) "
            + "AND (CAST(:query AS string) IS NULL "
            + "OR q.guestId IN :guestIds "
            + "OR LOWER(CONCAT(COALESCE(q.prospectFirstName, ''), ' ', COALESCE(q.prospectLastName, ''))) "
            + "LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) "
            + "OR LOWER(COALESCE(q.prospectEmail, '')) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))")
    Page<Quotation> searchByHotelId(
            @Param("hotelId") @NonNull UUID hotelId,
            @Param("statuses") @NonNull Collection<QuotationStatus> statuses,
            @Param("validFrom") LocalDate validFrom,
            @Param("validBefore") LocalDate validBefore,
            @Param("query") String query,
            @Param("guestIds") @NonNull List<UUID> guestIds,
            @NonNull Pageable pageable);

    /**
     * Finds a quotation by id, scoped to a hotel.
     *
     * @param id      the quotation UUID
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @return the quotation, if it exists and belongs to this hotel
     */
    Optional<Quotation> findByIdAndHotelId(@Param("id") @NonNull UUID id, @Param("hotelId") @NonNull UUID hotelId);

    /**
     * Whether any quotation of this hotel was mailed to the guest since {@code since} — the resend
     * cooldown is per recipient, so duplicating a quotation does not reset it.
     *
     * @param hotelId the hotel UUID (multi-tenant scoping)
     * @param guestId the guest UUID
     * @param since   the start of the cooldown window
     * @return true if a send happened inside the window
     */
    boolean existsByHotelIdAndGuestIdAndLastSentAtAfter(
            @NonNull UUID hotelId, @NonNull UUID guestId,
            @NonNull LocalDateTime since);

    /**
     * Same as {@link #existsByHotelIdAndGuestIdAndLastSentAtAfter} for a prospect, matched by email.
     *
     * @param hotelId       the hotel UUID (multi-tenant scoping)
     * @param prospectEmail the prospect's email, compared case-insensitively
     * @param since         the start of the cooldown window
     * @return true if a send happened inside the window
     */
    boolean existsByHotelIdAndProspectEmailIgnoreCaseAndLastSentAtAfter(
            @NonNull UUID hotelId, @NonNull String prospectEmail,
            @NonNull LocalDateTime since);
}
