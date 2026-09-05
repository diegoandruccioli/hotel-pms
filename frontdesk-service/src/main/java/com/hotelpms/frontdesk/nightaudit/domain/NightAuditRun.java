package com.hotelpms.frontdesk.nightaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable-once-COMPLETED closing record for a single (hotel, business
 * date): auto-detected no-shows, an occupancy/arrivals/departures snapshot,
 * and a cash-by-payment-method summary. See {@code V24__add_night_audit_runs.sql}.
 *
 * <p>No soft-delete {@code active} flag — unlike the rest of this codebase's
 * domain entities, this is an audit log, not a mutable resource. A FAILED row
 * is hard-deleted and replaced on retry (see {@code NightAuditServiceImpl}),
 * never soft-deleted.
 */
@Entity
@Table(name = "night_audit_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class NightAuditRun {

    private static final int MAX_STATUS_LENGTH = 20;
    private static final int MAX_RUN_BY_LENGTH = 100;
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = MAX_STATUS_LENGTH)
    private NightAuditStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Username that triggered the run, or {@code "system"} for the scheduled path. */
    @Column(name = "run_by", nullable = false, length = MAX_RUN_BY_LENGTH)
    private String runBy;

    @Column(name = "arrivals")
    private Integer arrivals;

    @Column(name = "departures")
    private Integer departures;

    @Column(name = "guests_in_house")
    private Long guestsInHouse;

    @Column(name = "current_stays")
    private Long currentStays;

    @Column(name = "available_rooms")
    private Integer availableRooms;

    @Column(name = "no_shows_marked")
    private Integer noShowsMarked;

    /**
     * JSON-serialized {@code List<PaymentMethodTotalDto>} from billing-service,
     * captured at run time. {@code null}/empty when {@link #cashSummaryDegraded}.
     */
    @Column(name = "cash_summary_json")
    private String cashSummaryJson;

    @Column(name = "cash_summary_degraded", nullable = false)
    private boolean cashSummaryDegraded;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON_LENGTH)
    private String failureReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
