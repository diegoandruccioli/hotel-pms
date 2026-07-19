package com.hotelpms.guest.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Guest entity representing a hotel guest.
 */
@Entity
@Table(name = "guests")
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE guests SET active = false WHERE id = ?")
@SQLRestriction("active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Guest {

    private static final int MAX_FIRST_NAME_LENGTH = 100;
    private static final int MAX_LAST_NAME_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 150;
    private static final int MAX_PHONE_LENGTH = 20;
    private static final int MAX_ADDRESS_LENGTH = 255;
    private static final int MAX_LOCATION_LENGTH = 50;
    private static final int MAX_FISCAL_CODE_LENGTH = 16;
    private static final int MAX_VAT_NUMBER_LENGTH = 20;
    private static final int MAX_COMPANY_NAME_LENGTH = 200;
    private static final int MAX_SDI_CODE_LENGTH = 7;
    private static final int MAX_CAP_LENGTH = 5;
    private static final int MAX_PROVINCIA_LENGTH = 2;
    private static final int MAX_COMUNE_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = MAX_FIRST_NAME_LENGTH)
    private String firstName;

    @Column(nullable = false, length = MAX_LAST_NAME_LENGTH)
    private String lastName;

    /**
     * The guest's date of birth. Required for Italian Alloggiati Web police
     * reporting.
     */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(nullable = true, length = MAX_EMAIL_LENGTH)
    private String email;

    @Column(length = MAX_PHONE_LENGTH)
    private String phone;

    @Column(length = MAX_ADDRESS_LENGTH)
    private String address;

    @Column(length = MAX_LOCATION_LENGTH)
    private String city;

    @Column(length = MAX_LOCATION_LENGTH)
    private String country;

    @Column(name = "fiscal_code", length = MAX_FISCAL_CODE_LENGTH)
    private String fiscalCode;

    @Column(name = "vat_number", length = MAX_VAT_NUMBER_LENGTH)
    private String vatNumber;

    @Column(name = "company_name", length = MAX_COMPANY_NAME_LENGTH)
    private String companyName;

    @Column(name = "sdi_code", length = MAX_SDI_CODE_LENGTH)
    private String sdiCode;

    @Column(name = "pec_email", length = MAX_EMAIL_LENGTH)
    private String pecEmail;

    /** CAP — Italian 5-digit postal code, required for a valid FatturaPA {@code Sede}. */
    @Column(name = "cap", length = MAX_CAP_LENGTH)
    private String cap;

    /**
     * Comune — municipality name, validated against the Portale Alloggiati Web
     * reference data (frontdesk-service) together with {@link #provincia}.
     */
    @Column(name = "comune", length = MAX_COMUNE_LENGTH)
    private String comune;

    /** Provincia — 2-letter province code (e.g. {@code "RM"}). */
    @Column(name = "provincia", length = MAX_PROVINCIA_LENGTH)
    private String provincia;

    @Builder.Default
    @OneToMany(mappedBy = "guest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IdentityDocument> identityDocuments = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Owning hotel UUID. Injected from the {@code X-Auth-Hotel} gateway header
     * and used to enforce multi-tenant isolation on every query.
     */
    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    /**
     * Date on which the guest provided GDPR consent (or the profile creation
     * date used as a legally defensible proxy). Used by the retention job and
     * the hard-delete legal-hold guard (T-GST-05).
     */
    @Column(name = "gdpr_consent_date", nullable = false)
    private LocalDate gdprConsentDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Adds an identity document to the guest and sets the guest reference in the
     * document.
     *
     * @param document the identity document to add
     */
    public void addIdentityDocument(final IdentityDocument document) {
        identityDocuments.add(document);
        document.setGuest(this);
    }

    /**
     * Removes an identity document from the guest and clears the guest reference in
     * the document.
     *
     * @param document the identity document to remove
     */
    public void removeIdentityDocument(final IdentityDocument document) {
        identityDocuments.remove(document);
        document.setGuest(null);
    }
}
