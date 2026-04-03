package com.hotelpms.guest.repository;

import com.hotelpms.guest.model.IdentityDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for IdentityDocument entity.
 */
@Repository
public interface IdentityDocumentRepository extends JpaRepository<IdentityDocument, UUID> {

    /**
     * Finds all identity documents by guest ID.
     *
     * @param guestId the guest ID
     * @return list of identity documents
     */
    List<IdentityDocument> findByGuestId(UUID guestId);

    /**
     * Finds an identity document by document number and type.
     *
     * @param documentNumber the document number
     * @param documentType   the document type enum string
     * @return the identity document if found
     */
    Optional<IdentityDocument> findByDocumentNumberAndDocumentType(
            String documentNumber,
            com.hotelpms.guest.model.enums.DocumentType documentType);
}
