package com.hotelpms.guest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.guest.dto.request.GuestRequest;
import com.hotelpms.guest.dto.request.IdentityDocumentRequestDTO;
import com.hotelpms.guest.dto.response.GuestDataExportResponse;
import com.hotelpms.guest.dto.response.GuestResponse;
import com.hotelpms.guest.dto.response.IdentityDocumentResponseDTO;
import com.hotelpms.guest.exception.GlobalExceptionHandler;
import com.hotelpms.guest.exception.NotFoundException;
import com.hotelpms.guest.model.enums.DocumentType;
import com.hotelpms.guest.service.GuestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GuestControllerTest {

    private static final String BASE_URL = "/api/v1/guests";
    private static final String PATH_BY_ID = "/{id}";
    private static final String JSON_ID = "$.id";
    private static final String JSON_FIRST_NAME = "$.firstName";
    private static final String TEST_FIRST_NAME = "John";
    private static final String TEST_LAST_NAME = "Doe";
    private static final String TEST_EMAIL = "john.doe@example.com";
    private static final String DOC_NUMBER = "AB123456";

    @Mock
    private GuestService guestService;

    @InjectMocks
    private GuestController guestController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID guestId;
    private GuestResponse guestResponse;

    @BeforeEach
    void setUp() {
        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(guestController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        guestId = UUID.randomUUID();
        guestResponse = new GuestResponse(
                guestId,
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_EMAIL,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                Collections.emptyList(),
                null, null, null);
    }

    @Test
    void shouldCreateGuestReturn201() throws Exception {
        final GuestRequest request = new GuestRequest(
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null);

        when(guestService.createGuest(any(GuestRequest.class))).thenReturn(guestResponse);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(JSON_ID).value(guestId.toString()))
                .andExpect(jsonPath(JSON_FIRST_NAME).value(TEST_FIRST_NAME));

        verify(guestService).createGuest(any(GuestRequest.class));
    }

    @Test
    void shouldGetGuestByIdReturn200() throws Exception {
        when(guestService.getGuestById(guestId)).thenReturn(guestResponse);

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, guestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(guestId.toString()))
                .andExpect(jsonPath(JSON_FIRST_NAME).value(TEST_FIRST_NAME));
    }

    @Test
    void shouldGetGuestByIdReturn404WhenNotFound() throws Exception {
        when(guestService.getGuestById(guestId))
                .thenThrow(new NotFoundException("GUEST_NOT_FOUND"));

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, guestId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllGuestsReturn200() throws Exception {
        final Page<GuestResponse> page = new PageImpl<>(
                List.of(guestResponse), PageRequest.of(0, 20), 1L);
        when(guestService.getAllGuests(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateGuestReturn200() throws Exception {
        final GuestRequest request = new GuestRequest(
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null);

        when(guestService.updateGuest(any(UUID.class), any(GuestRequest.class)))
                .thenReturn(guestResponse);

        mockMvc.perform(put(BASE_URL + PATH_BY_ID, guestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ID).value(guestId.toString()));

        verify(guestService).updateGuest(any(UUID.class), any(GuestRequest.class));
    }

    @Test
    void shouldDeleteGuestReturn204() throws Exception {
        doNothing().when(guestService).deleteGuest(guestId);

        mockMvc.perform(delete(BASE_URL + PATH_BY_ID, guestId))
                .andExpect(status().isNoContent());

        verify(guestService).deleteGuest(guestId);
    }

    @Test
    void shouldSearchGuestsReturn200() throws Exception {
        final Page<GuestResponse> page = new PageImpl<>(
                List.of(guestResponse), PageRequest.of(0, 20), 1L);
        when(guestService.searchGuests(any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(BASE_URL + "/search").param("query", TEST_FIRST_NAME))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetGuestsBatchReturn200() throws Exception {
        final List<UUID> ids = List.of(guestId);
        when(guestService.getGuestsByIds(any())).thenReturn(List.of(guestResponse));

        mockMvc.perform(post(BASE_URL + "/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value(TEST_FIRST_NAME));
    }

    @Test
    void shouldAddDocumentReturn201() throws Exception {
        final UUID docId = UUID.randomUUID();
        final IdentityDocumentRequestDTO docRequest = new IdentityDocumentRequestDTO(
                DocumentType.PASSPORT,
                DOC_NUMBER,
                LocalDate.now().minusYears(5),
                LocalDate.now().plusYears(5),
                "Italy");

        final IdentityDocumentResponseDTO docResponse = new IdentityDocumentResponseDTO(
                docId, DocumentType.PASSPORT, DOC_NUMBER,
                LocalDate.now().minusYears(5), LocalDate.now().plusYears(5),
                "Italy", null, null);

        when(guestService.addIdentityDocument(any(UUID.class), any(IdentityDocumentRequestDTO.class)))
                .thenReturn(docResponse);

        mockMvc.perform(post(BASE_URL + "/{id}/documents", guestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentNumber").value(DOC_NUMBER));
    }

    @Test
    void shouldRemoveDocumentReturn204() throws Exception {
        final UUID docId = UUID.randomUUID();
        doNothing().when(guestService).removeIdentityDocument(guestId, docId);

        mockMvc.perform(delete(BASE_URL + "/{id}/documents/{documentId}", guestId, docId))
                .andExpect(status().isNoContent());

        verify(guestService).removeIdentityDocument(guestId, docId);
    }

    @Test
    void shouldExportGuestDataReturn200() throws Exception {
        final GuestDataExportResponse exportResponse = new GuestDataExportResponse(
                LocalDateTime.now(), guestId,
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL,
                null, null, null, null, null, null, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        when(guestService.exportGuestData(guestId)).thenReturn(exportResponse);

        mockMvc.perform(get(BASE_URL + "/{id}/export", guestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestId").value(guestId.toString()))
                .andExpect(jsonPath(JSON_FIRST_NAME).value(TEST_FIRST_NAME));

        verify(guestService).exportGuestData(guestId);
    }
}
