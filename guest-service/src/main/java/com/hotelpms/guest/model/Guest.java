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

    @Column(nullable = false, unique = true, length = MAX_EMAIL_LENGTH)
    private String email;

    @Column(length = MAX_PHONE_LENGTH)
    private String phone;

    @Column(length = MAX_ADDRESS_LENGTH)
    private String address;

    @Column(length = MAX_LOCATION_LENGTH)
    private String city;

    @Column(length = MAX_LOCATION_LENGTH)
    private String country;

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
