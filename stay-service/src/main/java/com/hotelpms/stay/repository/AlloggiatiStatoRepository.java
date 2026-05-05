package com.hotelpms.stay.repository;

import com.hotelpms.stay.domain.AlloggiatiStato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for {@link AlloggiatiStato} lookup records.
 */
@Repository
public interface AlloggiatiStatoRepository extends JpaRepository<AlloggiatiStato, String> {

    /**
     * Returns all stati whose validity has not expired (dataFineVal is null or in the future).
     *
     * @param today today's date used as the upper bound for expiry check
     * @return list of active stati
     */
    List<AlloggiatiStato> findByDataFineValIsNullOrDataFineValAfter(LocalDate today);
}
