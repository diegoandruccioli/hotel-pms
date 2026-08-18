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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable per-stay tourist-tax calculation, one row per stay — never
 * recomputed after the fact, so the amount actually charged survives a later
 * rate change unchanged (fiscal audit requirement, same reasoning as
 * billing-service's {@code invoice_fiscal_exports} snapshots).
 *
 * <p>{@code cityTaxRateId} gives provenance (which rule/delibera applied);
 * the {@code *Snapshot} fields make the amount self-sufficiently
 * reconstructable even if that rule row were ever gone. A {@code null}
 * {@code cityTaxRateId} means no rule was configured at check-in time — the
 * row still records that "zero was assessed because unconfigured", distinct
 * from "assessed and zero because every guest was exempt".
 */
@Entity
@Table(name = "city_tax_assessments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityTaxAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(name = "stay_id", nullable = false)
    private UUID stayId;

    /** The rule applied, if any was configured — see class javadoc. */
    @Column(name = "city_tax_rate_id")
    private UUID cityTaxRateId;

    @Column(name = "amount_per_night_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPerNightSnapshot;

    @Column(name = "max_taxable_nights_snapshot")
    private Integer maxTaxableNightsSnapshot;

    @Column(name = "exempt_under_age_snapshot")
    private Integer exemptUnderAgeSnapshot;

    @Column(name = "taxable_guests", nullable = false)
    private int taxableGuests;

    @Column(name = "taxable_nights", nullable = false)
    private int taxableNights;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * The {@code CITY_TAX} charge id returned by billing-service, once posted.
     * {@code null} = not yet posted — the per-charge idempotency guard for the
     * check-in retry path, paired with {@code Stay.roomChargeId}.
     */
    @Column(name = "billing_charge_id")
    private UUID billingChargeId;

    @Column(name = "assessed_at", nullable = false)
    private LocalDateTime assessedAt;
}
