package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.stays.domain.AlloggiatiComune;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiStato;
import com.hotelpms.frontdesk.stays.domain.AlloggiatiTipdoc;
import com.hotelpms.frontdesk.stays.repository.AlloggiatiComuneRepository;
import com.hotelpms.frontdesk.stays.repository.AlloggiatiStatoRepository;
import com.hotelpms.frontdesk.stays.repository.AlloggiatiTipdocRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlloggiatiLookupServiceImpl}.
 * Verifies delegation to repositories and autocomplete query wiring.
 * Cache behavior is not asserted here (it requires a Spring context); see
 * {@code AlloggiatiCacheConfig} which is tested via the build integration.
 */
@ExtendWith(MockitoExtension.class)
class AlloggiatiLookupServiceImplTest {

    private static final String CODICE_ITALIA = "100000100";
    private static final String CODICE_ROMA = "058091000";
    private static final String DESC_ROMA = "Roma";
    private static final String PROVINCIA_RM = "RM";
    private static final String CODICE_PASOR = "PASOR";
    private static final String SEARCH_TERM = "rom";

    @Mock
    private AlloggiatiStatoRepository statoRepository;
    @Mock
    private AlloggiatiComuneRepository comuneRepository;
    @Mock
    private AlloggiatiTipdocRepository tipdocRepository;

    @InjectMocks
    private AlloggiatiLookupServiceImpl service;

    // -----------------------------------------------------------------------
    // findAllStati
    // -----------------------------------------------------------------------

    @Test
    void shouldDelegateToStatoRepositoryForFindAllStati() {
        final AlloggiatiStato stato = AlloggiatiStato.builder()
                .codice(CODICE_ITALIA).descrizione("ITALIA").build();
        when(statoRepository.findByDataFineValIsNullOrDataFineValAfter(any(LocalDate.class)))
                .thenReturn(List.of(stato));

        final List<AlloggiatiStato> result = service.findAllStati();

        assertEquals(1, result.size());
        assertEquals(CODICE_ITALIA, result.get(0).getCodice());
        verify(statoRepository).findByDataFineValIsNullOrDataFineValAfter(any(LocalDate.class));
    }

    // -----------------------------------------------------------------------
    // findAllComuni / findComuniByProvincia
    // -----------------------------------------------------------------------

    @Test
    void shouldDelegateToComuneRepositoryForFindAllComuni() {
        final AlloggiatiComune comune = AlloggiatiComune.builder()
                .codice(CODICE_ROMA).descrizione(DESC_ROMA).provincia(PROVINCIA_RM).build();
        when(comuneRepository.findActive(any(LocalDate.class))).thenReturn(List.of(comune));

        final List<AlloggiatiComune> result = service.findAllComuni();

        assertEquals(1, result.size());
        assertEquals(CODICE_ROMA, result.get(0).getCodice());
    }

    @Test
    void shouldDelegateToComuneRepositoryForFindByProvincia() {
        final AlloggiatiComune comune = AlloggiatiComune.builder()
                .codice(CODICE_ROMA).descrizione(DESC_ROMA).provincia(PROVINCIA_RM).build();
        when(comuneRepository.findActiveByProvincia(eq(PROVINCIA_RM), any(LocalDate.class)))
                .thenReturn(List.of(comune));

        final List<AlloggiatiComune> result = service.findComuniByProvincia(PROVINCIA_RM);

        assertEquals(1, result.size());
        assertEquals(PROVINCIA_RM, result.get(0).getProvincia());
    }

    // -----------------------------------------------------------------------
    // findComuneByCodice / findStatoByCodice / findTipdocByCodice
    // -----------------------------------------------------------------------

    @Test
    void shouldDelegateFindComuneByCodiceToPrimaryKey() {
        final AlloggiatiComune comune = AlloggiatiComune.builder()
                .codice(CODICE_ROMA).descrizione(DESC_ROMA).provincia(PROVINCIA_RM).build();
        when(comuneRepository.findById(CODICE_ROMA)).thenReturn(Optional.of(comune));

        final Optional<AlloggiatiComune> result = service.findComuneByCodice(CODICE_ROMA);

        assertTrue(result.isPresent());
        assertEquals(CODICE_ROMA, result.get().getCodice());
        verify(comuneRepository).findById(CODICE_ROMA);
    }

    @Test
    void shouldReturnEmptyWhenComuneNotFound() {
        when(comuneRepository.findById("999999999")).thenReturn(Optional.empty());

        assertTrue(service.findComuneByCodice("999999999").isEmpty());
    }

    @Test
    void shouldDelegateFindStatoByCodiceToPrimaryKey() {
        final AlloggiatiStato stato = AlloggiatiStato.builder()
                .codice(CODICE_ITALIA).descrizione("ITALIA").build();
        when(statoRepository.findById(CODICE_ITALIA)).thenReturn(Optional.of(stato));

        assertTrue(service.findStatoByCodice(CODICE_ITALIA).isPresent());
    }

    @Test
    void shouldDelegateFindTipdocByCodiceToPrimaryKey() {
        final AlloggiatiTipdoc tipdoc = AlloggiatiTipdoc.builder()
                .codice(CODICE_PASOR).descrizione("PASSAPORTO ORDINARIO").build();
        when(tipdocRepository.findById(CODICE_PASOR)).thenReturn(Optional.of(tipdoc));

        assertTrue(service.findTipdocByCodice(CODICE_PASOR).isPresent());
    }

    // -----------------------------------------------------------------------
    // findAllTipdoc
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnAllTipdoc() {
        final AlloggiatiTipdoc tipdoc = AlloggiatiTipdoc.builder()
                .codice(CODICE_PASOR).descrizione("PASSAPORTO ORDINARIO").build();
        when(tipdocRepository.findAll()).thenReturn(List.of(tipdoc));

        final List<AlloggiatiTipdoc> result = service.findAllTipdoc();

        assertEquals(1, result.size());
        assertEquals(CODICE_PASOR, result.get(0).getCodice());
    }

    // -----------------------------------------------------------------------
    // searchComuni — autocomplete
    // -----------------------------------------------------------------------

    @Test
    void shouldDelegateSearchComuniWithoutProvinciaToRepository() {
        final AlloggiatiComune comune = AlloggiatiComune.builder()
                .codice(CODICE_ROMA).descrizione(DESC_ROMA).provincia(PROVINCIA_RM).build();
        when(comuneRepository.searchActive(eq(SEARCH_TERM), isNull(), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of(comune));

        final List<AlloggiatiComune> result = service.searchComuni(SEARCH_TERM, null);

        assertEquals(1, result.size());
        verify(comuneRepository).searchActive(eq(SEARCH_TERM), isNull(), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void shouldPassProvinciaToSearchWhenProvided() {
        when(comuneRepository.searchActive(eq(SEARCH_TERM), eq(PROVINCIA_RM), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of());

        service.searchComuni(SEARCH_TERM, PROVINCIA_RM);

        verify(comuneRepository).searchActive(eq(SEARCH_TERM), eq(PROVINCIA_RM), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void shouldNormaliseBlankProvinciaToNull() {
        when(comuneRepository.searchActive(eq(SEARCH_TERM), isNull(), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of());

        service.searchComuni(SEARCH_TERM, "   ");

        verify(comuneRepository).searchActive(eq(SEARCH_TERM), isNull(), any(LocalDate.class), any(Pageable.class));
    }
}
