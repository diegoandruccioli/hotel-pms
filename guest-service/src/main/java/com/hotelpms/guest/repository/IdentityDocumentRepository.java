package com.hotelpms.guest.repository;

import com.hotelpms.guest.model.IdentityDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository interface for IdentityDocument entity.
 *
 * <p>Declares no custom query methods: every real call site uses only the
 * inherited {@code findById}/{@code save}/{@code delete}, always guarded by
 * first resolving the owning guest within the caller's hotel (see
 * {@code GuestServiceImpl.removeIdentityDocument}, which checks
 * {@code document.getGuest().getId().equals(guestId)} after the fetch).
 * {@code findByGuestId} and {@code findByDocumentNumberAndDocumentType} were
 * removed here (Point 7 item 2, tenant-isolation audit): both were unscoped
 * global lookups across every hotel's documents and had zero callers anywhere
 * in the codebase — dead code that was also the one real cross-tenant gap on
 * this repository.
 */
@Repository
public interface IdentityDocumentRepository extends JpaRepository<IdentityDocument, UUID> {
}
