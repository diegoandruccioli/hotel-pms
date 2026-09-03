package com.hotelpms.frontdesk.stays.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;

/**
 * Entity class representing a Stay in the hotel.
 * A Stay links a Guest, a Reservation, and a Room.
 */
@Entity
@Table(name = "stays")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE stays SET active = false WHERE id = ?")
@SQLRestriction("active = true")
public class Stay {

    /**
     * Maximum length for the status column.
     */
    public static final int MAX_STATUS_LENGTH = 20;

    /**
     * Unique identifier for the Stay.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The hotel this stay belongs to (multi-tenancy support).
     */
    @Column(name = "hotel_id")
    private UUID hotelId;

    /**
     * The ID of the reservation associated with this stay; {@code null} for
     * walk-in stays, which have no prior reservation.
     */
    @Column(name = "reservation_id")
    private UUID reservationId;

    /**
     * The ID of the guest associated with this stay.
     */
    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    /**
     * The ID of the room associated with this stay.
     */
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    /**
     * Denormalized primary guest display name ("Cognome Nome") captured at check-in.
     * Null for stays created before this field was introduced.
     */
    @Column(name = "guest_display_name")
    private String guestDisplayName;

    /**
     * Denormalized room number captured at check-in.
     * Null for stays created before this field was introduced.
     */
    @Column(name = "room_number")
    private String roomNumber;

    /**
     * The current status of the stay.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = MAX_STATUS_LENGTH)
    private StayStatus status;

    /**
     * The actual time the guest checked in.
     */
    @Column(name = "actual_check_in_time")
    private LocalDateTime actualCheckInTime;

    /**
     * The actual time the guest checked out.
     */
    @Column(name = "actual_check_out_time")
    private LocalDateTime actualCheckOutTime;

    /**
     * The timestamp when the record was created.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * The timestamp when the record was last updated.
     */
    @LastModifiedDate
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    /**
     * Flag indicating whether the record is active (for soft deletion).
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Reference to the billing invoice folio opened at check-in (cross-domain,
     * no DB FK since billing remains a separate microservice).
     */
    @Column(name = "invoice_id")
    private UUID invoiceId;

    /**
     * The billing-service id of the {@code ROOM_NIGHT} charge posted at
     * check-in, once posted. {@code null} means "not yet posted" — the
     * per-charge idempotency guard for the check-in retry path (E18: paired
     * with {@code CityTaxAssessment.billingChargeId} for the {@code CITY_TAX}
     * charge, so a failure on one charge no longer causes a retry to
     * re-post the other).
     */
    @Column(name = "room_charge_id")
    private UUID roomChargeId;

    /**
     * Per-night price {@code roomChargeId} was actually posted at, snapshotted
     * alongside it (check-in, or the last room change) — never re-derived
     * live. A room change (Parte 6) needs this to price the already-consumed
     * nights at what the guest actually agreed to, not a live re-quote
     * against today's rates (same "an amount already charged never silently
     * changes" principle as {@code CityTaxAssessment}). {@code null} for
     * stays checked in before this field existed.
     */
    @Column(name = "room_charge_unit_price", precision = 10, scale = 2)
    private BigDecimal roomChargeUnitPrice;

    /**
     * Night count {@code roomChargeId} covers, snapshotted alongside {@link
     * #roomChargeUnitPrice}.
     */
    @Column(name = "room_charge_nights")
    private Integer roomChargeNights;

    /**
     * Whether the Alloggiati Web report for this stay has been successfully submitted
     * to the Polizia di Stato portal (either automatically at check-in or manually).
     */
    @Column(name = "alloggiati_sent", nullable = false)
    private boolean alloggiatiSent;

    /**
     * Whether the most recent Alloggiati Web submission attempt for this stay failed.
     * Cleared (set back to {@code false}) as soon as a later attempt succeeds — this is
     * the signal that drives the FAILED state of the per-stay badge and the Dashboard
     * alert banner, distinct from "never attempted" ({@code alloggiatiSent=false} and
     * this field also {@code false}, e.g. auto-send disabled for the hotel).
     */
    @Column(name = "alloggiati_send_failed", nullable = false)
    private boolean alloggiatiSendFailed;

    /**
     * The error message from the most recent failed Alloggiati Web submission attempt,
     * for display in the Dashboard alert banner. {@code null} once the failure is resolved.
     */
    @Column(name = "alloggiati_failure_reason")
    private String alloggiatiFailureReason;

    /**
     * Whether the most recent billing-invoice-creation attempt at check-in failed
     * (billing-service circuit breaker open or call failed). Mirrors
     * {@link #alloggiatiSendFailed} — cleared as soon as a retry succeeds.
     */
    @Column(name = "invoice_creation_failed", nullable = false)
    private boolean invoiceCreationFailed;

    /**
     * The reason for the most recent failed invoice-creation attempt, for staff
     * visibility. {@code null} once resolved.
     */
    @Column(name = "invoice_creation_failure_reason")
    private String invoiceCreationFailureReason;

    /**
     * Whether the most recent checkout summary email attempt failed
     * (notification-service circuit breaker open or call failed). Mirrors
     * {@link #alloggiatiSendFailed} — cleared as soon as a retry succeeds.
     */
    @Column(name = "checkout_email_failed", nullable = false)
    private boolean checkoutEmailFailed;

    /**
     * The reason for the most recent failed checkout email attempt, for staff
     * visibility. {@code null} once resolved.
     */
    @Column(name = "checkout_email_failure_reason")
    private String checkoutEmailFailureReason;

    /**
     * Expected check-out date sourced from the reservation at check-in time.
     * Used to calculate the {@code permanenza} (number of nights) in the Alloggiati tracciato.
     * May be {@code null} for stays created before this feature was introduced.
     */
    @Column(name = "expected_check_out_date")
    private LocalDate expectedCheckOutDate;

    /**
     * The list of guests staying in this room.
     */
    @OneToMany(mappedBy = "stay", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StayGuest> guests = new ArrayList<>();

    /**
     * Optimistic-locking version, checked against a client-echoed version on
     * {@code extendStay} — same "forgotten tab" protection {@code Reservation.version}
     * already has.
     */
    @Version
    @Column(name = "version")
    private Long version;
}
