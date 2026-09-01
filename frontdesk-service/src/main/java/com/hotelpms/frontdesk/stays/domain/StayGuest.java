package com.hotelpms.frontdesk.stays.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity class representing a guest attached to a Stay (for Alloggiati Web compliance).
 */
@Entity
@Table(name = "stay_guests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE stay_guests SET active = false WHERE id = ?")
@SQLRestriction("active = true")
public class StayGuest {

    private static final int TRAVELLER_TYPE_LENGTH = 20;
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "stay_id", nullable = false)
    private Stay stay;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "gender", nullable = false)
    private String gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "place_of_birth", nullable = false)
    private String placeOfBirth;

    @Column(name = "citizenship", nullable = false)
    private String citizenship;

    /** Nullable for FAMILIARE/MEMBRO_GRUPPO (TIPALLOG 19/20) per tracciato rules. */
    @Column(name = "document_type")
    private String documentType;

    /** Nullable for FAMILIARE/MEMBRO_GRUPPO (TIPALLOG 19/20) per tracciato rules. */
    @Column(name = "document_number")
    private String documentNumber;

    /** Nullable for FAMILIARE/MEMBRO_GRUPPO (TIPALLOG 19/20) per tracciato rules. */
    @Column(name = "document_place_of_issue")
    private String documentPlaceOfIssue;

    @Column(name = "is_primary_guest", nullable = false)
    private boolean isPrimaryGuest;

    @Enumerated(EnumType.STRING)
    @Column(name = "traveller_type", length = TRAVELLER_TYPE_LENGTH)
    private TravellerType travellerType;

    @Column(name = "travel_purpose", length = 100)
    private String travelPurpose;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * The date this guest actually arrived — drives which Alloggiati Web daily
     * report they belong to. Equal to the stay's check-in date for a guest
     * present at check-in; distinct (and later) for a guest added mid-stay.
     */
    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    /**
     * The date this guest departed, when different from the room's own
     * check-out (e.g. one of two guests leaving early). {@code null} while
     * the guest is still in house.
     */
    @Column(name = "departure_date")
    private LocalDate departureDate;

    /**
     * Whether this guest's own Alloggiati Web schedina has been transmitted —
     * the per-person grain the portal actually recognizes, distinct from
     * {@code Stay.alloggiatiSent} which reflects the room's initial batch.
     */
    @Column(name = "alloggiati_sent", nullable = false)
    private boolean alloggiatiSent;

    /** When {@link #alloggiatiSent} last became {@code true}. */
    @Column(name = "alloggiati_sent_at")
    private LocalDateTime alloggiatiSentAt;

    /**
     * Set when a guest already sent to Alloggiati Web is corrected afterward.
     * The portal has no rectification API — the fix is a full resubmission,
     * so this guest must be picked up by the next report run regardless of
     * their {@link #arrivalDate}.
     */
    @Column(name = "needs_resubmit", nullable = false)
    private boolean needsResubmit;

    /** Optimistic-locking version — this row is mutable after check-in (Parte 1). */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * The billing-service charge id posted for this guest's own tourist-tax
     * contribution when they were added mid-stay ({@code
     * CityTaxAssessmentService#rectifyForGuestAdded}) — {@code null} for a guest
     * present at check-in (their tax is part of the stay's original assessment, not
     * attributable to one guest) or when no charge was actually posted (invoice
     * closed, amount zero, or the charge call failed). Lets {@code removeGuest} void
     * exactly this charge, and only this one, if the guest is later removed.
     */
    @Column(name = "city_tax_charge_id")
    private UUID cityTaxChargeId;

    /**
     * The amount charged under {@link #cityTaxChargeId}, kept alongside it so the
     * assessment's running total can be corrected even if the billing-service
     * reversal itself fails or the invoice has since closed.
     */
    @Column(name = "city_tax_charge_amount")
    private BigDecimal cityTaxChargeAmount;
}
