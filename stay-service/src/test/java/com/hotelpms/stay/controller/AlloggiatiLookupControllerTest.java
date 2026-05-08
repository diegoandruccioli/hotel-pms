package com.hotelpms.stay.controller;

import com.hotelpms.stay.domain.AlloggiatiComune;
import com.hotelpms.stay.domain.AlloggiatiStato;
import com.hotelpms.stay.domain.AlloggiatiTipdoc;
import com.hotelpms.stay.service.AlloggiatiLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link AlloggiatiLookupController}.
 * Uses {@code standaloneSetup} to avoid loading the Spring context (and config server bootstrap).
 */
@ExtendWith(MockitoExtension.class)
class AlloggiatiLookupControllerTest {

    private static final String PATH_STATI = "/api/v1/stays/lookup/stati";
    private static final String PATH_COMUNI = "/api/v1/stays/lookup/comuni";
    private static final String PATH_TIPDOC = "/api/v1/stays/lookup/tipdoc";

    private static final String CODICE_ITALIA = "100000100";
    private static final String CODICE_ROMA = "058091000";
    private static final String PROVINCIA_RM = "RM";
    private static final String CODICE_PASOR = "PASOR";
    private static final String SEARCH_TERM = "rom";
    private static final String DESC_ROMA = "Roma";
    private static final String PARAM_Q = "q";
    private static final String PARAM_PROV = "provincia";
    private static final String JSON_CODICE_0 = "$[0].codice";
    private static final String JSON_DESC_0 = "$[0].descrizione";
    private static final String JSON_PROV_0 = "$[0].provincia";

    @Mock
    private AlloggiatiLookupService lookupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AlloggiatiLookupController(lookupService)).build();
    }

    // -----------------------------------------------------------------------
    // GET /stati
    // -----------------------------------------------------------------------

    @Test
    void statiReturnsAllActiveStati() throws Exception {
        when(lookupService.findAllStati()).thenReturn(List.of(
                AlloggiatiStato.builder().codice(CODICE_ITALIA).descrizione("ITALIA").build()));

        mockMvc.perform(get(PATH_STATI))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_CODICE_0).value(CODICE_ITALIA))
                .andExpect(jsonPath(JSON_DESC_0).value("ITALIA"));
    }

    @Test
    void statiReturnsEmptyArrayWhenNoData() throws Exception {
        when(lookupService.findAllStati()).thenReturn(List.of());

        mockMvc.perform(get(PATH_STATI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // -----------------------------------------------------------------------
    // GET /comuni
    // -----------------------------------------------------------------------

    @Test
    void comuniWithQueryParamDelegatesToSearchComuni() throws Exception {
        when(lookupService.searchComuni(eq(SEARCH_TERM), isNull())).thenReturn(List.of(
                AlloggiatiComune.builder().codice(CODICE_ROMA).descrizione(DESC_ROMA).provincia(PROVINCIA_RM).build()));

        mockMvc.perform(get(PATH_COMUNI).param(PARAM_Q, SEARCH_TERM))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_CODICE_0).value(CODICE_ROMA))
                .andExpect(jsonPath(JSON_DESC_0).value(DESC_ROMA))
                .andExpect(jsonPath(JSON_PROV_0).value(PROVINCIA_RM));

        verify(lookupService).searchComuni(eq(SEARCH_TERM), isNull());
        verify(lookupService, never()).findAllComuni();
    }

    @Test
    void comuniWithQueryAndProvinciaPassesProvinciaToSearch() throws Exception {
        when(lookupService.searchComuni(eq(SEARCH_TERM), eq(PROVINCIA_RM))).thenReturn(List.of());

        mockMvc.perform(get(PATH_COMUNI).param(PARAM_Q, SEARCH_TERM).param(PARAM_PROV, PROVINCIA_RM))
                .andExpect(status().isOk());

        verify(lookupService).searchComuni(SEARCH_TERM, PROVINCIA_RM);
    }

    @Test
    void comuniWithOnlyProvinciaDelegatesToFindByProvincia() throws Exception {
        when(lookupService.findComuniByProvincia(PROVINCIA_RM)).thenReturn(List.of(
                AlloggiatiComune.builder().codice(CODICE_ROMA).descrizione(DESC_ROMA).provincia(PROVINCIA_RM).build()));

        mockMvc.perform(get(PATH_COMUNI).param(PARAM_PROV, PROVINCIA_RM))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_PROV_0).value(PROVINCIA_RM));

        verify(lookupService).findComuniByProvincia(PROVINCIA_RM);
        verify(lookupService, never()).searchComuni(any(), any());
    }

    @Test
    void comuniWithNoParamsDelegatesToFindAllComuni() throws Exception {
        when(lookupService.findAllComuni()).thenReturn(List.of());

        mockMvc.perform(get(PATH_COMUNI))
                .andExpect(status().isOk());

        verify(lookupService).findAllComuni();
    }

    @Test
    void comuniWithBlankQueryParamDelegatesToFindAllComuni() throws Exception {
        when(lookupService.findAllComuni()).thenReturn(List.of());

        mockMvc.perform(get(PATH_COMUNI).param(PARAM_Q, "   "))
                .andExpect(status().isOk());

        verify(lookupService).findAllComuni();
    }

    // -----------------------------------------------------------------------
    // GET /tipdoc
    // -----------------------------------------------------------------------

    @Test
    void tipdocReturnsAllTipdoc() throws Exception {
        when(lookupService.findAllTipdoc()).thenReturn(List.of(
                AlloggiatiTipdoc.builder().codice(CODICE_PASOR).descrizione("PASSAPORTO ORDINARIO").build()));

        mockMvc.perform(get(PATH_TIPDOC))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_CODICE_0).value(CODICE_PASOR));
    }
}
