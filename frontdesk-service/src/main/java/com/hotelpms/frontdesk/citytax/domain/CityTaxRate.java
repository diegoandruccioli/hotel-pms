package com.hotelpms.frontdesk.citytax.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A versioned tourist-tax rate rule for one hotel, one comune, one category,
 * over a date range (art. 4 D.Lgs. 23/2011). Append-only: a tariff change
 * closes the current row ({@code validTo}) and inserts a new one — never
 * mutated or logically removed once a {@code CityTaxAssessment} can
 * reference it, so unlike most entities in this codebase there is
 * deliberately no {@code @SQLDelete}/soft-delete and no {@code @Version}
 * here. The {@code excl_city_tax_rates_no_overlap} GiST constraint (same
 * role as {@code rate_seasons}' equivalent) is the concurrency guard.
 */
@Entity
@Table(name = "city_tax_rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CityTaxRate {

    private static final int MAX_CATEGORY_LENGTH = 20;
    private static final int MAX_COMUNE_CODICE_LENGTH = 9;
    private static final int MAX_NOTE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    /** Tenant root — deliberately per-hotel, not shared across the comune. See class javadoc. */
    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(name = "comune_codice", nullable = false, length = MAX_COMUNE_CODICE_LENGTH)
    private String comuneCodice;

    /** Free-form classification label, same convention as {@link HotelCategoryHistory#getCategory()}. */
    @Column(nullable = false, length = MAX_CATEGORY_LENGTH)
    private String category;

    @Column(name = "amount_per_night", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPerNight;

    /** Cap on taxable nights; {@code null} = uncapped. */
    @Column(name = "max_taxable_nights")
    private Integer maxTaxableNights;

    /** Guests strictly under this age at check-in are exempt; {@code null} = no age exemption. */
    @Column(name = "exempt_under_age")
    private Integer exemptUnderAge;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** {@code null} = this is the currently active rule. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(length = MAX_NOTE_LENGTH)
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
