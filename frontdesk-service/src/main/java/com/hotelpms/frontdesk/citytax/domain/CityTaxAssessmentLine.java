package com.hotelpms.frontdesk.citytax.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One homogeneous-rate segment of a {@link CityTaxAssessment} — a stay whose
 * nights cross a delibera boundary produces one line per rate actually in
 * effect, rather than one rate applied to the whole stay. {@code
 * CityTaxAssessment}'s own {@code total_amount} is the sum of every line's
 * {@code subtotal}, kept as a denormalized aggregate for the common case
 * (display, the billing charge) — these lines are the fiscally authoritative
 * breakdown, the grain a comune declaration actually needs (presenze per
 * periodo) and what makes the total independently verifiable in an audit.
 *
 * <p>Immutable once written, same as {@link CityTaxAssessment} itself: a
 * rectification (Parte 1 guest added, Parte 3 extension) adds new lines for
 * the additional period, it never edits an existing line.
 */
@Entity
@Table(name = "city_tax_assessment_lines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CityTaxAssessmentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    /** Start of this segment (inclusive). */
    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    /** End of this segment (exclusive) — the night before is the last taxed night. */
    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    /** The rule actually in effect for this segment. */
    @Column(name = "city_tax_rate_id", nullable = false)
    private UUID cityTaxRateId;

    @Column(name = "amount_per_night", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPerNight;

    @Column(name = "taxable_guests", nullable = false)
    private int taxableGuests;

    @Column(name = "taxable_nights", nullable = false)
    private int taxableNights;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
