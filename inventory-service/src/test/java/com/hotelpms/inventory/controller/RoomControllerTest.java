package com.hotelpms.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.inventory.domain.RoomStatus;
import com.hotelpms.inventory.dto.RoomRequest;
import com.hotelpms.inventory.dto.RoomResponse;
import com.hotelpms.inventory.dto.RoomStatusRequest;
import com.hotelpms.inventory.exception.GlobalExceptionHandler;
import com.hotelpms.inventory.exception.NotFoundException;
import com.hotelpms.inventory.service.RoomService;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    private static final String BASE_URL = "/api/v1/rooms";
    private static final String PATH_BY_ID = "/{id}";
    private static final String PATH_STATUS = "/{id}/status";
    private static final String JSON_ROOM_NUMBER = "$.roomNumber";
    private static final String ROOM_NUMBER_101 = "101";
    private static final UUID HOTEL_ID = UUID.randomUUID();

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomController roomController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID roomId;
    private UUID roomTypeId;
    private RoomResponse roomResponse;

    @BeforeEach
    void setUp() {
        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testuser", "", List.of());
        auth.setDetails(HOTEL_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(roomController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        roomId = UUID.randomUUID();
        roomTypeId = UUID.randomUUID();

        roomResponse = new RoomResponse(
                roomId, HOTEL_ID, ROOM_NUMBER_101, null, RoomStatus.CLEAN, true, null, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateRoomReturn201() throws Exception {
        final RoomRequest request =
                new RoomRequest(HOTEL_ID, ROOM_NUMBER_101, roomTypeId, RoomStatus.CLEAN);

        when(roomService.createRoom(any(RoomRequest.class))).thenReturn(roomResponse);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(roomId.toString()))
                .andExpect(jsonPath(JSON_ROOM_NUMBER).value(ROOM_NUMBER_101));

        verify(roomService).createRoom(any(RoomRequest.class));
    }

    @Test
    void shouldGetRoomByIdReturn200() throws Exception {
        when(roomService.getRoomById(any(UUID.class), any(UUID.class))).thenReturn(roomResponse);

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId.toString()))
                .andExpect(jsonPath(JSON_ROOM_NUMBER).value(ROOM_NUMBER_101));
    }

    @Test
    void shouldGetRoomByIdReturn404WhenNotFound() throws Exception {
        when(roomService.getRoomById(any(UUID.class), any(UUID.class)))
                .thenThrow(new NotFoundException("ROOM_NOT_FOUND"));

        mockMvc.perform(get(BASE_URL + PATH_BY_ID, roomId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllRoomsReturn200() throws Exception {
        final Page<RoomResponse> page = new PageImpl<>(
                List.of(roomResponse), PageRequest.of(0, 20), 1L);
        when(roomService.getAllRooms(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateRoomReturn200() throws Exception {
        final RoomRequest request =
                new RoomRequest(HOTEL_ID, ROOM_NUMBER_101, roomTypeId, RoomStatus.CLEAN);
        when(roomService.updateRoom(any(UUID.class), any(UUID.class), any(RoomRequest.class)))
                .thenReturn(roomResponse);

        mockMvc.perform(put(BASE_URL + PATH_BY_ID, roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_ROOM_NUMBER).value(ROOM_NUMBER_101));

        verify(roomService).updateRoom(any(UUID.class), any(UUID.class), any(RoomRequest.class));
    }

    @Test
    void shouldUpdateRoomStatusReturn200() throws Exception {
        final RoomStatusRequest statusRequest = new RoomStatusRequest(RoomStatus.DIRTY);
        final RoomResponse dirtyResponse = new RoomResponse(
                roomId, HOTEL_ID, ROOM_NUMBER_101, null, RoomStatus.DIRTY, true, null, null);

        when(roomService.updateRoomStatus(any(UUID.class), any(UUID.class), any(RoomStatus.class)))
                .thenReturn(dirtyResponse);

        mockMvc.perform(patch(BASE_URL + PATH_STATUS, roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DIRTY"));
    }

    @Test
    void shouldReturnValidationErrorWhenStatusBodyIsNull() throws Exception {
        final String body = "{\"status\": null}";

        mockMvc.perform(patch(BASE_URL + PATH_STATUS, roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteRoomReturn204() throws Exception {
        doNothing().when(roomService).deleteRoom(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete(BASE_URL + PATH_BY_ID, roomId))
                .andExpect(status().isNoContent());

        verify(roomService).deleteRoom(any(UUID.class), any(UUID.class));
    }
}
