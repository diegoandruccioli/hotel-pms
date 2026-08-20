package com.hotelpms.frontdesk.rooms.service.impl;

import com.hotelpms.frontdesk.rooms.domain.Room;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.domain.RoomType;
import com.hotelpms.frontdesk.rooms.dto.RoomRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.rooms.mapper.RoomMapper;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.rooms.repository.RoomTypeRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        response = new RoomResponse(roomId, hotelId, ROOM_101, null, RoomStatus.CLEAN, true, null, null, null);
    }

    @Test
    void testCreateRoomSuccess() {
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(roomTypeId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(roomType));
        when(roomMapper.toEntity(Objects.requireNonNull(request))).thenReturn(room);
        when(roomRepository.save(Objects.requireNonNull(room))).thenReturn(room);
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        final RoomResponse result = roomService.createRoom(request, hotelId);

        assertNotNull(result);
        assertEquals(ROOM_101, result.roomNumber());
        verify(roomRepository).save(Objects.requireNonNull(room));
    }

    @Test
    void testCreateRoomIgnoresHotelIdFromRequestBody() {
        final UUID requestHotelId = UUID.randomUUID();
        final UUID authenticatedHotelId = UUID.randomUUID();
        final RoomRequest crossTenantRequest = new RoomRequest(requestHotelId, ROOM_101, roomTypeId, RoomStatus.CLEAN);

        when(roomTypeRepository.findByIdAndHotelId(
                Objects.requireNonNull(roomTypeId), Objects.requireNonNull(authenticatedHotelId)))
                .thenReturn(Optional.of(roomType));
        when(roomMapper.toEntity(Objects.requireNonNull(crossTenantRequest))).thenReturn(room);
        when(roomRepository.save(Objects.requireNonNull(room))).thenReturn(room);
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        roomService.createRoom(crossTenantRequest, authenticatedHotelId);

        assertEquals(authenticatedHotelId, room.getHotelId());
    }

    @Test
    void testCreateRoomRoomTypeNotFound() {
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(roomTypeId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomService.createRoom(request, hotelId));
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

        when(roomRepository.findAllByActiveTrueAndHotelId(hotelId, pageable)).thenReturn(roomPage);
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        final Page<RoomResponse> result = roomService.getAllRooms(pageable, hotelId);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(ROOM_101, result.getContent().get(0).roomNumber());
    }

    @Test
    void testGetAllRoomsEmptyPage() {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Room> emptyPage = Page.empty(pageable);

        when(roomRepository.findAllByActiveTrueAndHotelId(hotelId, pageable)).thenReturn(emptyPage);

        final Page<RoomResponse> result = roomService.getAllRooms(pageable, hotelId);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void testGetAllRoomsWithStatusOnlyFilter() {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Room> roomPage = new PageImpl<>(List.of(Objects.requireNonNull(room)), pageable, 1L);

        when(roomRepository.findAllByActiveTrueAndHotelIdAndStatus(hotelId, RoomStatus.CLEAN, pageable))
                .thenReturn(roomPage);
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        final Page<RoomResponse> result = roomService.getAllRooms(pageable, hotelId, RoomStatus.CLEAN, null);

        assertEquals(1, result.getTotalElements());
        verify(roomRepository).findAllByActiveTrueAndHotelIdAndStatus(hotelId, RoomStatus.CLEAN, pageable);
    }

    @Test
    void testGetAllRoomsWithRoomTypeOnlyFilter() {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Room> roomPage = new PageImpl<>(List.of(Objects.requireNonNull(room)), pageable, 1L);

        when(roomRepository.findAllByActiveTrueAndHotelIdAndRoomTypeId(hotelId, roomTypeId, pageable))
                .thenReturn(roomPage);
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        final Page<RoomResponse> result = roomService.getAllRooms(pageable, hotelId, null, roomTypeId);

        assertEquals(1, result.getTotalElements());
        verify(roomRepository).findAllByActiveTrueAndHotelIdAndRoomTypeId(hotelId, roomTypeId, pageable);
    }

    @Test
    void testGetAllRoomsWithStatusAndRoomTypeFilter() {
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<Room> roomPage = new PageImpl<>(List.of(Objects.requireNonNull(room)), pageable, 1L);

        when(roomRepository.findAllByActiveTrueAndHotelIdAndStatusAndRoomTypeId(
                hotelId, RoomStatus.CLEAN, roomTypeId, pageable))
                .thenReturn(roomPage);
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        final Page<RoomResponse> result = roomService.getAllRooms(pageable, hotelId, RoomStatus.CLEAN, roomTypeId);

        assertEquals(1, result.getTotalElements());
        verify(roomRepository).findAllByActiveTrueAndHotelIdAndStatusAndRoomTypeId(
                hotelId, RoomStatus.CLEAN, roomTypeId, pageable);
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
                null, null, null);

        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(roomTypeId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(roomType));
        when(roomRepository.saveAndFlush(Objects.requireNonNull(room))).thenReturn(updatedRoom);
        when(roomMapper.toResponse(Objects.requireNonNull(updatedRoom))).thenReturn(updateResponse);

        final RoomResponse result = roomService.updateRoom(roomId, hotelId, updateRequest);

        assertEquals(ROOM_102, result.roomNumber());
        assertEquals(RoomStatus.DIRTY, result.status());
        verify(roomRepository).saveAndFlush(Objects.requireNonNull(room));
    }

    @Test
    void testUpdateRoomIgnoresHotelIdFromRequestBody() {
        final UUID requestHotelId = UUID.randomUUID();
        final RoomRequest crossTenantRequest = new RoomRequest(requestHotelId, ROOM_102, roomTypeId, RoomStatus.DIRTY);

        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(roomTypeId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(roomType));
        when(roomRepository.saveAndFlush(Objects.requireNonNull(room))).thenReturn(room);
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        roomService.updateRoom(roomId, hotelId, crossTenantRequest);

        assertEquals(hotelId, room.getHotelId());
    }

    @Test
    void testUpdateRoomRejectsOccupiedTarget() {
        final RoomRequest occupiedRequest = new RoomRequest(hotelId, ROOM_101, roomTypeId, RoomStatus.OCCUPIED);

        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(roomId, hotelId)).thenReturn(Optional.of(room));

        assertThrows(BadRequestException.class, () -> roomService.updateRoom(roomId, hotelId, occupiedRequest));
    }

    @Test
    void testUpdateRoomRejectsWhenRoomCurrentlyOccupied() {
        final Room occupiedRoom = Room.builder()
                .id(roomId)
                .hotelId(hotelId)
                .roomNumber(ROOM_101)
                .roomType(roomType)
                .status(RoomStatus.OCCUPIED)
                .active(true)
                .build();
        final RoomRequest maintenanceRequest = new RoomRequest(hotelId, ROOM_101, roomTypeId, RoomStatus.MAINTENANCE);

        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(roomId, hotelId))
                .thenReturn(Optional.of(occupiedRoom));

        assertThrows(ConflictException.class, () -> roomService.updateRoom(roomId, hotelId, maintenanceRequest));
    }

    @Test
    void testUpdateRoomAllowsNonStatusChangesWhileOccupied() {
        final Room occupiedRoom = Room.builder()
                .id(roomId)
                .hotelId(hotelId)
                .roomNumber(ROOM_101)
                .roomType(roomType)
                .status(RoomStatus.OCCUPIED)
                .active(true)
                .build();
        final RoomRequest sameStatusRequest = new RoomRequest(hotelId, ROOM_102, roomTypeId, RoomStatus.OCCUPIED);
        final RoomResponse renamedResponse = new RoomResponse(roomId, hotelId, ROOM_102, null, RoomStatus.OCCUPIED,
                true, null, null, null);

        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(roomId, hotelId))
                .thenReturn(Optional.of(occupiedRoom));
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(roomTypeId), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(roomType));
        when(roomRepository.saveAndFlush(Objects.requireNonNull(occupiedRoom))).thenReturn(occupiedRoom);
        when(roomMapper.toResponse(Objects.requireNonNull(occupiedRoom))).thenReturn(renamedResponse);

        final RoomResponse result = roomService.updateRoom(roomId, hotelId, sameStatusRequest);

        assertEquals(ROOM_102, result.roomNumber());
        assertEquals(RoomStatus.OCCUPIED, result.status());
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
                null, null, null);

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
                roomId, hotelId, ROOM_101, null, RoomStatus.OCCUPIED, true, null, null, null);

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
    void testUpdateHousekeepingStatusSuccess() {
        final Room maintenanceRoom = Room.builder()
                .id(roomId)
                .hotelId(hotelId)
                .roomNumber(ROOM_101)
                .roomType(roomType)
                .status(RoomStatus.MAINTENANCE)
                .active(true)
                .build();
        final RoomResponse maintenanceResponse = new RoomResponse(
                roomId, hotelId, ROOM_101, null, RoomStatus.MAINTENANCE, true, null, null, null);

        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(Objects.requireNonNull(room))).thenReturn(maintenanceRoom);
        when(roomMapper.toResponse(Objects.requireNonNull(maintenanceRoom))).thenReturn(maintenanceResponse);

        final RoomResponse result = roomService.updateHousekeepingStatus(roomId, hotelId, RoomStatus.MAINTENANCE);

        assertEquals(RoomStatus.MAINTENANCE, result.status());
        verify(roomRepository).saveAndFlush(Objects.requireNonNull(room));
    }

    @Test
    void testUpdateHousekeepingStatusRejectsOccupiedTarget() {
        assertThrows(BadRequestException.class,
                () -> roomService.updateHousekeepingStatus(roomId, hotelId, RoomStatus.OCCUPIED));
    }

    @Test
    void testUpdateHousekeepingStatusRejectsWhenRoomCurrentlyOccupied() {
        final Room occupiedRoom = Room.builder()
                .id(roomId)
                .hotelId(hotelId)
                .roomNumber(ROOM_101)
                .roomType(roomType)
                .status(RoomStatus.OCCUPIED)
                .active(true)
                .build();

        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(roomId, hotelId)).thenReturn(Optional.of(occupiedRoom));

        assertThrows(ConflictException.class,
                () -> roomService.updateHousekeepingStatus(roomId, hotelId, RoomStatus.MAINTENANCE));
    }

    @Test
    void testUpdateHousekeepingStatusNotFound() {
        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(roomId, hotelId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> roomService.updateHousekeepingStatus(roomId, hotelId, RoomStatus.MAINTENANCE));
    }

    @Test
    void testUpdateHousekeepingStatusBulkAppliesToEveryRoom() {
        final UUID secondRoomId = UUID.randomUUID();
        final Room secondRoom = Room.builder()
                .id(secondRoomId)
                .hotelId(hotelId)
                .roomNumber(ROOM_102)
                .roomType(roomType)
                .status(RoomStatus.DIRTY)
                .active(true)
                .build();
        final Room updatedFirst = Room.builder()
                .id(roomId).hotelId(hotelId).roomNumber(ROOM_101).roomType(roomType)
                .status(RoomStatus.MAINTENANCE).active(true).build();
        final Room updatedSecond = Room.builder()
                .id(secondRoomId).hotelId(hotelId).roomNumber(ROOM_102).roomType(roomType)
                .status(RoomStatus.MAINTENANCE).active(true).build();
        final RoomResponse firstResponse = new RoomResponse(
                roomId, hotelId, ROOM_101, null, RoomStatus.MAINTENANCE, true, null, null, null);
        final RoomResponse secondResponse = new RoomResponse(
                secondRoomId, hotelId, ROOM_102, null, RoomStatus.MAINTENANCE, true, null, null, null);

        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(secondRoomId, hotelId))
                .thenReturn(Optional.of(secondRoom));
        when(roomRepository.saveAndFlush(Objects.requireNonNull(room))).thenReturn(updatedFirst);
        when(roomRepository.saveAndFlush(secondRoom)).thenReturn(updatedSecond);
        when(roomMapper.toResponse(updatedFirst)).thenReturn(firstResponse);
        when(roomMapper.toResponse(updatedSecond)).thenReturn(secondResponse);

        final List<RoomResponse> results = roomService.updateHousekeepingStatusBulk(
                List.of(roomId, secondRoomId), hotelId, RoomStatus.MAINTENANCE);

        assertEquals(2, results.size());
        assertEquals(firstResponse, results.get(0));
        assertEquals(secondResponse, results.get(1));
    }

    @Test
    void testUpdateHousekeepingStatusBulkPropagatesGuardViolation() {
        final UUID occupiedRoomId = UUID.randomUUID();
        final Room occupiedRoom = Room.builder()
                .id(occupiedRoomId).hotelId(hotelId).roomNumber(ROOM_102).roomType(roomType)
                .status(RoomStatus.OCCUPIED).active(true).build();

        when(roomRepository.findByIdAndActiveTrueAndHotelIdForUpdate(occupiedRoomId, hotelId))
                .thenReturn(Optional.of(occupiedRoom));

        assertThrows(ConflictException.class, () -> roomService.updateHousekeepingStatusBulk(
                List.of(occupiedRoomId), hotelId, RoomStatus.MAINTENANCE));
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

    @Test
    void testFindCleanRoomsSuccess() {
        when(roomRepository.findAllByActiveTrueAndHotelIdAndStatus(hotelId, RoomStatus.CLEAN))
                .thenReturn(List.of(room));
        when(roomMapper.toResponse(Objects.requireNonNull(room))).thenReturn(response);

        final List<RoomResponse> result = roomService.findCleanRooms(hotelId);

        assertEquals(1, result.size());
        assertEquals(ROOM_101, result.get(0).roomNumber());
    }

    @Test
    void testFindCleanRoomsEmpty() {
        when(roomRepository.findAllByActiveTrueAndHotelIdAndStatus(hotelId, RoomStatus.CLEAN))
                .thenReturn(List.of());

        final List<RoomResponse> result = roomService.findCleanRooms(hotelId);

        assertTrue(result.isEmpty());
    }
}
