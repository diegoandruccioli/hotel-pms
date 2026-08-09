package com.hotelpms.frontdesk.quotations.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A priced stay offer, convertible to a {@code Reservation}. Deliberately a
 * standalone entity, not a {@code Reservation} in a tentative status: a
 * quotation must never block room availability, count as an "active
 * reservation" for the guest-service GDPR legal-hold guard, or appear in the
 * reservations list/calendar.
 *
 * <p>{@code guestId} is nullable — a quotation is often made for a prospect
 * who calls in before ever becoming a persisted {@code Guest}. Either
 * {@code guestId} or the {@code prospectFirstName}/{@code prospectLastName}/
 * {@code prospectEmail} triple is present (DB-level {@code CHECK}, V10).
 */
@Entity
@Table(name = "quotations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE quotations SET active = false WHERE id = ?")
@SQLRestriction("active = true")
public class Quotation {

    private static final int MAX_STATUS_LENGTH = 20;
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(name = "guest_id")
    private UUID guestId;

    @Column(name = "prospect_first_name")
    private String prospectFirstName;

    @Column(name = "prospect_last_name")
    private String prospectLastName;

    @Column(name = "prospect_email")
    private String prospectEmail;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "expected_guests")
    private Integer expectedGuests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = MAX_STATUS_LENGTH)
    private QuotationStatus status;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "send_failed", nullable = false)
    private boolean sendFailed;

    @Column(name = "send_failure_reason", length = MAX_FAILURE_REASON_LENGTH)
    private String sendFailureReason;

    /** The option the guest accepted at conversion time; unset until then. */
    @Column(name = "accepted_option_id")
    private UUID acceptedOptionId;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @SQLRestriction("active = true")
    private List<QuotationOption> options = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Whether this quotation is past its validity window and not already in
     * a terminal state — the computed {@code EXPIRED} state (never persisted).
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return (status == QuotationStatus.DRAFT || status == QuotationStatus.SENT)
                && validUntil.isBefore(LocalDate.now());
    }
}
