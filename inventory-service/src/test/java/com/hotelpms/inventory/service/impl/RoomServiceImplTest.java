package com.hotelpms.inventory.service.impl;

import com.hotelpms.inventory.domain.Room;
import com.hotelpms.inventory.domain.RoomStatus;
import com.hotelpms.inventory.domain.RoomType;
import com.hotelpms.inventory.dto.RoomRequest;
import com.hotelpms.inventory.dto.RoomResponse;
import com.hotelpms.inventory.exception.NotFoundException;
import com.hotelpms.inventory.mapper.RoomMapper;
import com.hotelpms.inventory.repository.RoomRepository;
import com.hotelpms.inventory.repository.RoomTypeRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    private static final String ROOM_101 = "101";
    private static final String ROOM_102 = "102";

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private RoomMapper roomMapper;

    @InjectMocks
    private RoomServiceImpl roomService;

    private Room room;
    private RoomType roomType;
    private RoomRequest request;
    private RoomResponse response;
    private UUID roomId;
    private UUID roomTypeId;
    private UUID hotelId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        roomTypeId = UUID.randomUUID();
        hotelId = UUID.randomUUID();

        roomType = RoomType.builder()
                .id(roomTypeId)
                .name("Single")
                .active(true)
                .build();

        room = Room.builder()
                .id(roomId)
                .hotelId(hotelId)
                .roomNumber(ROOM_101)
                .roomType(roomType)
                .status(RoomStatus.CLEAN)
                .active(true)
                .build();

        request = new RoomRequest(hotelId, ROOM_101, roomTypeId, RoomStatus.CLEAN);

        response = new RoomResponse(roomId, hotelId, ROOM_101, null, RoomStatus.CLEAN, true, null, null);
    }

    @Test
    void testCreateRoomSuccess() {
        when(roomTypeRepository.findById(Objects.requireNonNull(roomTypeId))).thenReturn(Optional.of(roomType));
        when(roomMapper.toEntity(Objects.requireNonNull(request))).thenReturn(room);
        when(roomRepository.save(Objects.requireNonNull(room))).thenReturn(room);
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        final RoomResponse result = roomService.createRoom(request);

        assertNotNull(result);
        assertEquals(ROOM_101, result.roomNumber());
        verify(roomRepository).save(Objects.requireNonNull(room));
    }

    @Test
    void testCreateRoomRoomTypeNotFound() {
        when(roomTypeRepository.findById(Objects.requireNonNull(roomTypeId))).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomService.createRoom(request));
    }

    @Test
    void testGetRoomByIdSuccess() {
        when(roomRepository.findByIdAndActiveTrueAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        final RoomResponse result = roomService.getRoomById(roomId, hotelId);

        assertNotNull(result);
        assertEquals(roomId, result.id());
    }

    @Test
    void testGetRoomByIdNotFound() {
        when(roomRepository.findByIdAndActiveTrueAndHotelId(roomId, hotelId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomService.getRoomById(roomId, hotelId));
    }

    @Test
    void testGetRoomByIdWrongHotelReturnsNotFound() {
        final UUID otherHotelId = UUID.randomUUID();
        when(roomRepository.findByIdAndActiveTrueAndHotelId(roomId, otherHotelId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomService.getRoomById(roomId, otherHotelId));
    }

    @Test
    void testGetAllRoomsSuccess() {
        final Pageable pageable = PageRequest.of(0, 20);
        final List<Room> activeRooms = new ArrayList<>(List.of(room));
        final Page<Room> roomPage = new PageImpl<>(activeRooms, pageable, 1L);

        when(roomRepository.findAllByActiveTrue(pageable)).thenReturn(roomPage);
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        final Page<RoomResponse> result = roomService.getAllRooms(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(ROOM_101, result.getContent().get(0).roomNumber());
    }

    @Test
    void testGetAllRoomsEmptyPage() {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Room> emptyPage = Page.empty(pageable);

        when(roomRepository.findAllByActiveTrue(pageable)).thenReturn(emptyPage);

        final Page<RoomResponse> result = roomService.getAllRooms(pageable);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void testUpdateRoomSuccess() {
        final RoomRequest updateRequest = new RoomRequest(hotelId, ROOM_102, roomTypeId, RoomStatus.DIRTY);
        final Room updatedRoom = Room.builder()
                .id(roomId)
                .hotelId(hotelId)
                .roomNumber(ROOM_102)
                .roomType(roomType)
                .status(RoomStatus.DIRTY)
                .active(true)
                .build();
        final RoomResponse updateResponse = new RoomResponse(roomId, hotelId, ROOM_102, null, RoomStatus.DIRTY, true,
                null, null);

        when(roomRepository.findByIdAndActiveTrueAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomTypeRepository.findById(Objects.requireNonNull(roomTypeId))).thenReturn(Optional.of(roomType));
        when(roomRepository.saveAndFlush(Objects.requireNonNull(room))).thenReturn(updatedRoom);
        when(roomMapper.toResponse(Objects.requireNonNull(updatedRoom))).thenReturn(updateResponse);

        final RoomResponse result = roomService.updateRoom(roomId, hotelId, updateRequest);

        assertEquals(ROOM_102, result.roomNumber());
        assertEquals(RoomStatus.DIRTY, result.status());
        verify(roomRepository).saveAndFlush(Objects.requireNonNull(room));
    }

    @Test
    void testUpdateRoomStatusSuccess() {
        final Room dirtyRoom = Room.builder()
                .id(roomId)
                .hotelId(hotelId)
                .roomNumber(ROOM_101)
                .roomType(roomType)
                .status(RoomStatus.DIRTY)
                .active(true)
                .build();
        final RoomResponse dirtyResponse = new RoomResponse(roomId, hotelId, ROOM_101, null, RoomStatus.DIRTY, true,
                null, null);

        when(roomRepository.findByIdAndActiveTrueAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(Objects.requireNonNull(room))).thenReturn(dirtyRoom);
        when(roomMapper.toResponse(Objects.requireNonNull(dirtyRoom))).thenReturn(dirtyResponse);

        final RoomResponse result = roomService.updateRoomStatus(roomId, hotelId, RoomStatus.DIRTY);

        assertEquals(RoomStatus.DIRTY, result.status());
        verify(roomRepository).saveAndFlush(Objects.requireNonNull(room));
    }

    @Test
    void testUpdateRoomStatusToOccupied() {
        final Room occupiedRoom = Room.builder()
                .id(roomId)
                .hotelId(hotelId)
                .roomNumber(ROOM_101)
                .roomType(roomType)
                .status(RoomStatus.OCCUPIED)
                .active(true)
                .build();
        final RoomResponse occupiedResponse = new RoomResponse(
                roomId, hotelId, ROOM_101, null, RoomStatus.OCCUPIED, true, null, null);

        when(roomRepository.findByIdAndActiveTrueAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(Objects.requireNonNull(room))).thenReturn(occupiedRoom);
        when(roomMapper.toResponse(Objects.requireNonNull(occupiedRoom))).thenReturn(occupiedResponse);

        final RoomResponse result = roomService.updateRoomStatus(roomId, hotelId, RoomStatus.OCCUPIED);

        assertEquals(RoomStatus.OCCUPIED, result.status());
        verify(roomRepository).saveAndFlush(Objects.requireNonNull(room));
    }

    @Test
    void testUpdateRoomStatusNotFound() {
        when(roomRepository.findByIdAndActiveTrueAndHotelId(roomId, hotelId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> roomService.updateRoomStatus(roomId, hotelId, RoomStatus.MAINTENANCE));
    }

    @Test
    void testDeleteRoomSuccess() {
        when(roomRepository.findByIdAndActiveTrueAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));

        roomService.deleteRoom(roomId, hotelId);

        verify(roomRepository).delete(Objects.requireNonNull(room));
    }

    @Test
    void testDeleteRoomNotFound() {
        when(roomRepository.findByIdAndActiveTrueAndHotelId(roomId, hotelId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomService.deleteRoom(roomId, hotelId));
    }
}
