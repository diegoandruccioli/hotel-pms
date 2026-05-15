package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.domain.AlloggiatiComune;
import com.hotelpms.stay.domain.AlloggiatiStato;
import com.hotelpms.stay.domain.AlloggiatiTipdoc;
import com.hotelpms.stay.repository.AlloggiatiComuneRepository;
import com.hotelpms.stay.repository.AlloggiatiStatoRepository;
import com.hotelpms.stay.repository.AlloggiatiTipdocRepository;
import com.hotelpms.stay.config.AlloggiatiCacheConfig;
import com.hotelpms.stay.service.AlloggiatiLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link AlloggiatiLookupService}.
 * All reads are read-only transactions.
 */
@SuppressWarnings("null")
@Service
@RequiredArgsConstructor
@Slf4j
public class AlloggiatiLookupServiceImpl implements AlloggiatiLookupService {

    private static final int AUTOCOMPLETE_MAX_RESULTS = 20;

    private final AlloggiatiStatoRepository statoRepository;
    private final AlloggiatiComuneRepository comuneRepository;
    private final AlloggiatiTipdocRepository tipdocRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiStato> findAllStati() {
        log.debug("Fetching all active stati");
        return statoRepository.findByDataFineValIsNullOrDataFineValAfter(LocalDate.now());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiComune> findAllComuni() {
        log.debug("Fetching all active comuni");
        return comuneRepository.findActive(LocalDate.now());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiComune> findComuniByProvincia(final String provincia) {
        log.debug("Fetching active comuni for provincia: {}", provincia);
        return comuneRepository.findActiveByProvincia(provincia, LocalDate.now());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiTipdoc> findAllTipdoc() {
        log.debug("Fetching all tipdoc");
        return tipdocRepository.findAll();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = AlloggiatiCacheConfig.CACHE_STATI, cacheManager = "alloggiatiCacheManager")
    public Optional<AlloggiatiStato> findStatoByCodice(final String codice) {
        log.debug("Fetching stato by codice: {}", codice);
        return statoRepository.findById(codice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = AlloggiatiCacheConfig.CACHE_COMUNI, cacheManager = "alloggiatiCacheManager")
    public Optional<AlloggiatiComune> findComuneByCodice(final String codice) {
        log.debug("Fetching comune by codice: {}", codice);
        return comuneRepository.findById(codice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = AlloggiatiCacheConfig.CACHE_TIPDOC, cacheManager = "alloggiatiCacheManager")
    public Optional<AlloggiatiTipdoc> findTipdocByCodice(final String codice) {
        log.debug("Fetching tipdoc by codice: {}", codice);
        return tipdocRepository.findById(codice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiComune> searchComuni(final String query, final String provincia) {
        log.debug("Searching comuni query='{}' provincia='{}'", query, provincia);
        return comuneRepository.searchActive(
                query,
                (provincia != null && !provincia.isBlank()) ? provincia : null,
                LocalDate.now(),
                PageRequest.of(0, AUTOCOMPLETE_MAX_RESULTS));
    }
}
