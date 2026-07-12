package com.hotelpms.billing.repository;

import com.hotelpms.billing.domain.InvoiceSequence;
import com.hotelpms.billing.domain.InvoiceSequenceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository per {@link InvoiceSequence}.
 * Il metodo {@link #findByHotelIdAndYearForUpdate} acquisisce un lock pessimistico
 * a livello di riga — indispensabile per garantire unicità e assenza di gap nella
 * numerazione progressiva delle fatture sotto carico concorrente.
 */
@Repository
public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, InvoiceSequenceId> {

    /**
     * Legge il contatore per (hotelId, year) con lock PESSIMISTIC_WRITE.
     * Deve essere chiamato all'interno di un contesto {@code @Transactional} attivo.
     *
     * @param hotelId hotel tenant
     * @param year    anno solare
     * @return il contatore corrente se esiste, altrimenti empty
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.id.hotelId = :hotelId AND s.id.year = :year")
    Optional<InvoiceSequence> findByHotelIdAndYearForUpdate(
            @Param("hotelId") UUID hotelId,
            @Param("year") int year);
}
