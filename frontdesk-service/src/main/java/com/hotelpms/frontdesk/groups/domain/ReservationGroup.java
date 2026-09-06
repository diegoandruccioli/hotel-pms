package com.hotelpms.frontdesk.groups.domain;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ResultCheckStyle;
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
 * A named block of rooms booked together (company/agency/party), with an optional
 * group rate (takes precedence over the calendar for member reservations) and an
 * optional master folio (billing-service invoice all member rooms marked
 * "billed to group" settle onto instead of their own individual invoice).
 */
@Entity
@Table(name = "reservation_groups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(
        sql = "UPDATE reservation_groups SET active = false, version = version + 1 WHERE id = ? AND version = ?",
        check = ResultCheckStyle.COUNT)
@SQLRestriction("active = true")
public class ReservationGroup {

    private static final int LEN_STATUS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(nullable = false)
    private String name;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "contact_guest_id", nullable = false)
    private UUID contactGuestId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = LEN_STATUS)
    private GroupStatus status;

    @Column(name = "group_rate_per_night")
    private BigDecimal groupRatePerNight;

    @Column(name = "master_folio_invoice_id")
    private UUID masterFolioInvoiceId;

    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
