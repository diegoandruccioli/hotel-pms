package com.hotelpms.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.inventory.dto.RoomTypeRequest;
import com.hotelpms.inventory.dto.RoomTypeResponse;
import com.hotelpms.inventory.exception.GlobalExceptionHandler;
import com.hotelpms.inventory.exception.NotFoundException;
import com.hotelpms.inventory.service.RoomTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class RoomTypeControllerTest {

    private static final String BASE_URL = "/api/v1/room-types";
    private static final String PATH_BY_ID = "/{id}";
    private static final String JSON_NAME = "$.name";
    private static final String ROOM_TYPE_SINGLE = "Single";
    private static final String ROOM_TYPE_DESC = "A single room";
    private static final String PRICE_50 = "50.00";

    @Mock
    private RoomTypeService roomTypeService;

    @InjectMocks
    private RoomTypeController roomTypeController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID roomTypeId;
    private RoomTypeResponse roomTypeResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(roomTypeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        roomTypeId = UUID.randomUUID();
        roomTypeResponse = new RoomTypeResponse(
                roomTypeId, ROOM_TYPE_SINGLE, ROOM_TYPE_DESC, 1, new BigDecimal(PRICE_50), true, null, null);
    }

    @Test
    void shouldCreateRoomTypeReturn201() throws Exception {
        final RoomTypeRequest request =
                new RoomTypeRequest(ROOM_TYPE_SINGLE, ROOM_TYPE_DESC, 1, new BigDecimal(PRICE_50));

        when(roomTypeService.createRoomType(any(RoomTypeRequest.class))).thenReturn(roomTypeResponse);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(roomTypeId.toString()))
                .andExpect(jsonPath(JSON_NAME).value(ROOM_TYPE_SINGLE));

        verify(roomTypeService).createRoomType(any(RoomTypeRequest.class));
    }

    @Test
    void shouldGetRoomTypeByIdReturn200() throws Exception {
        when(roomTypeService.getRoomTypeById(roomTypeId)).thenReturn(roomTypeResponse);

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, roomTypeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomTypeId.toString()))
                .andExpect(jsonPath(JSON_NAME).value(ROOM_TYPE_SINGLE));
    }

    @Test
    void shouldGetRoomTypeByIdReturn404WhenNotFound() throws Exception {
        when(roomTypeService.getRoomTypeById(roomTypeId))
                .thenThrow(new NotFoundException("ROOM_TYPE_NOT_FOUND"));

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, roomTypeId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllRoomTypesReturn200() throws Exception {
        when(roomTypeService.getAllRoomTypes()).thenReturn(List.of(roomTypeResponse));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(ROOM_TYPE_SINGLE));
    }

    @Test
    void shouldUpdateRoomTypeReturn200() throws Exception {
        final RoomTypeRequest request =
                new RoomTypeRequest(ROOM_TYPE_SINGLE, ROOM_TYPE_DESC, 1, new BigDecimal(PRICE_50));

        when(roomTypeService.updateRoomType(any(UUID.class), any(RoomTypeRequest.class)))
                .thenReturn(roomTypeResponse);

        mockMvc.perform(put(BASE_URL + PATH_BY_ID, roomTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_NAME).value(ROOM_TYPE_SINGLE));

        verify(roomTypeService).updateRoomType(any(UUID.class), any(RoomTypeRequest.class));
    }

    @Test
    void shouldDeleteRoomTypeReturn204() throws Exception {
        doNothing().when(roomTypeService).deleteRoomType(roomTypeId);

        mockMvc.perform(delete(BASE_URL + PATH_BY_ID, roomTypeId))
                .andExpect(status().isNoContent());

        verify(roomTypeService).deleteRoomType(roomTypeId);
    }
}
