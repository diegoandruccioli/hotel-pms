package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.stays.config.AlloggiatiCacheConfig;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiComune;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiStato;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiTipdoc;
import com.hotelpms.frontdesk.stays.dto.AlloggiatiComuneResponse;
import com.hotelpms.frontdesk.stays.dto.AlloggiatiStatoResponse;
import com.hotelpms.frontdesk.stays.dto.AlloggiatiTipdocResponse;
import com.hotelpms.frontdesk.stays.repository.AlloggiatiComuneRepository;
import com.hotelpms.frontdesk.stays.repository.AlloggiatiStatoRepository;
import com.hotelpms.frontdesk.stays.repository.AlloggiatiTipdocRepository;
import com.hotelpms.frontdesk.stays.service.AlloggiatiLookupService;
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
    public List<AlloggiatiStatoResponse> findAllStati() {
        log.debug("Fetching all active stati");
        return statoRepository.findByDataFineValIsNullOrDataFineValAfter(LocalDate.now()).stream()
                .map(AlloggiatiLookupServiceImpl::toResponse)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiComuneResponse> findAllComuni() {
        log.debug("Fetching all active comuni");
        return toResponses(comuneRepository.findActive(LocalDate.now()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiComuneResponse> findComuniByProvincia(final String provincia) {
        log.debug("Fetching active comuni for provincia: {}", sanitizeForLog(provincia));
        return toResponses(comuneRepository.findActiveByProvincia(provincia, LocalDate.now()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiTipdocResponse> findAllTipdoc() {
        log.debug("Fetching all tipdoc");
        return tipdocRepository.findAll().stream()
                .map(AlloggiatiLookupServiceImpl::toResponse)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = AlloggiatiCacheConfig.CACHE_STATI, cacheManager = "alloggiatiCacheManager")
    public Optional<AlloggiatiStato> findStatoByCodice(final String codice) {
        log.debug("Fetching stato by codice: {}", sanitizeForLog(codice));
        return statoRepository.findById(codice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = AlloggiatiCacheConfig.CACHE_COMUNI, cacheManager = "alloggiatiCacheManager")
    public Optional<AlloggiatiComune> findComuneByCodice(final String codice) {
        log.debug("Fetching comune by codice: {}", sanitizeForLog(codice));
        return comuneRepository.findById(codice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = AlloggiatiCacheConfig.CACHE_TIPDOC, cacheManager = "alloggiatiCacheManager")
    public Optional<AlloggiatiTipdoc> findTipdocByCodice(final String codice) {
        log.debug("Fetching tipdoc by codice: {}", sanitizeForLog(codice));
        return tipdocRepository.findById(codice);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AlloggiatiComuneResponse> searchComuni(final String query, final String provincia) {
        log.debug("Searching comuni query='{}' provincia='{}'", sanitizeForLog(query), sanitizeForLog(provincia));
        return toResponses(comuneRepository.searchActive(
                query,
                (provincia != null && !provincia.isBlank()) ? provincia : null,
                LocalDate.now(),
                PageRequest.of(0, AUTOCOMPLETE_MAX_RESULTS)));
    }

    private static List<AlloggiatiComuneResponse> toResponses(final List<AlloggiatiComune> comuni) {
        return comuni.stream().map(AlloggiatiLookupServiceImpl::toResponse).toList();
    }

    private static AlloggiatiStatoResponse toResponse(final AlloggiatiStato stato) {
        return new AlloggiatiStatoResponse(stato.getCodice(), stato.getDescrizione(), stato.getDataFineVal());
    }

    private static AlloggiatiComuneResponse toResponse(final AlloggiatiComune comune) {
        return new AlloggiatiComuneResponse(
                comune.getCodice(), comune.getDescrizione(), comune.getProvincia(), comune.getDataFineVal());
    }

    private static AlloggiatiTipdocResponse toResponse(final AlloggiatiTipdoc tipdoc) {
        return new AlloggiatiTipdocResponse(tipdoc.getCodice(), tipdoc.getDescrizione());
    }

    private static String sanitizeForLog(final String value) {
        return value == null ? null : value.replace("\r", "_").replace("\n", "_");
    }
}
