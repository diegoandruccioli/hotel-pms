package com.hotelpms.guest.model;

import com.hotelpms.guest.model.enums.DocumentType;
import com.hotelpms.guest.security.DocumentNumberConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import java.util.UUID;

/**
 * IdentityDocument entity representing a guest's identification.
 */
@Entity
@Table(name = "identity_documents")
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE identity_documents SET active = false WHERE id = ?")
@SQLRestriction("active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentityDocument {

    /** Encrypted ciphertext (IV + tag + encoding overhead) needs far more room than the plaintext. */
    private static final int ENCRYPTED_DOCUMENT_NUMBER_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    /**
     * Encrypted at rest (E23, GDPR Art. 32) via {@link DocumentNumberConverter} —
     * this field always holds the decrypted plaintext in memory, never ciphertext.
     */
    @Convert(converter = DocumentNumberConverter.class)
    @Column(nullable = false, length = ENCRYPTED_DOCUMENT_NUMBER_LENGTH)
    private String documentNumber;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Column(length = 100)
    private String issuingCountry;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
