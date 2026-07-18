package com.hotelpms.frontdesk.rooms.service.impl;

import com.hotelpms.frontdesk.rooms.domain.RoomType;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.rooms.mapper.RoomTypeMapper;
import com.hotelpms.frontdesk.rooms.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class RoomTypeServiceImplTest {

    private static final String SINGLE = "Single";
    private static final String SINGLE_DESC = "A single room";
    private static final String DOUBLE = "Double";
    private static final String DOUBLE_DESC = "A double room";
    private static final String PRICE_50 = "50.00";
    private static final String PRICE_100 = "100.00";

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private RoomTypeMapper roomTypeMapper;

    @InjectMocks
    private RoomTypeServiceImpl roomTypeService;

    private RoomType roomType;
    private RoomTypeRequest request;
    private RoomTypeResponse response;
    private UUID id;
    private UUID hotelId;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        hotelId = UUID.randomUUID();
        roomType = RoomType.builder()
                .id(id)
                .hotelId(hotelId)
                .name(SINGLE)
                .description(SINGLE_DESC)
                .maxOccupancy(1)
                .basePrice(new BigDecimal(PRICE_50))
                .active(true)
                .build();

        request = new RoomTypeRequest(SINGLE, SINGLE_DESC, 1, new BigDecimal(PRICE_50));

        response = new RoomTypeResponse(id, SINGLE, SINGLE_DESC, 1, new BigDecimal(PRICE_50), true, null, null);
    }

    @Test
    void testCreateRoomTypeSuccess() {
        when(roomTypeMapper.toEntity(Objects.requireNonNull(request))).thenReturn(roomType);
        when(roomTypeRepository.saveAndFlush(Objects.requireNonNull(roomType))).thenReturn(roomType);
        when(roomTypeMapper.toResponse(Objects.requireNonNull(roomType))).thenReturn(response);

        final RoomTypeResponse result = roomTypeService.createRoomType(request, hotelId);

        assertNotNull(result);
        assertEquals(SINGLE, result.name());
        assertEquals(hotelId, roomType.getHotelId());
        verify(roomTypeRepository).saveAndFlush(Objects.requireNonNull(roomType));
    }

    @Test
    void testGetRoomTypeByIdSuccess() {
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(roomType));
        when(roomTypeMapper.toResponse(Objects.requireNonNull(roomType))).thenReturn(response);

        final RoomTypeResponse result = roomTypeService.getRoomTypeById(id, hotelId);

        assertNotNull(result);
        assertEquals(id, result.id());
    }

    @Test
    void testGetRoomTypeByIdNotFound() {
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomTypeService.getRoomTypeById(id, hotelId));
    }

    @Test
    void testGetRoomTypeByIdBelongsToDifferentHotelReturnsNotFound() {
        // Regression test for T-ROOM-02: a room type UUID from another hotel must
        // never resolve, even though it exists in the database.
        final UUID otherHotelId = UUID.randomUUID();
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(otherHotelId)))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomTypeService.getRoomTypeById(id, otherHotelId));
    }

    @Test
    void testGetAllRoomTypesSuccess() {
        when(roomTypeRepository.findAllByHotelId(Objects.requireNonNull(hotelId))).thenReturn(List.of(roomType));
        when(roomTypeMapper.toResponse(Objects.requireNonNull(roomType))).thenReturn(response);

        final List<RoomTypeResponse> result = roomTypeService.getAllRoomTypes(hotelId);

        assertEquals(1, result.size());
        assertEquals(SINGLE, result.get(0).name());
    }

    @Test
    void testUpdateRoomTypeSuccess() {
        final RoomTypeRequest updateRequest = new RoomTypeRequest(DOUBLE, DOUBLE_DESC, 2, new BigDecimal(PRICE_100));
        final RoomType updatedRoomType = RoomType.builder()
                .id(id)
                .hotelId(hotelId)
                .name(DOUBLE)
                .active(true)
                .build();
        final RoomTypeResponse updateResponse = new RoomTypeResponse(id, DOUBLE, DOUBLE_DESC, 2,
                new BigDecimal(PRICE_100), true, null, null);

        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(roomType));
        when(roomTypeRepository.saveAndFlush(Objects.requireNonNull(roomType))).thenReturn(updatedRoomType);
        when(roomTypeMapper.toResponse(Objects.requireNonNull(updatedRoomType))).thenReturn(updateResponse);

        final RoomTypeResponse result = roomTypeService.updateRoomType(id, hotelId, updateRequest);

        assertEquals(DOUBLE, result.name());
        verify(roomTypeRepository).saveAndFlush(Objects.requireNonNull(roomType)); // The active roomType is updated
    }

    @Test
    void testUpdateRoomTypeNotFound() {
        final RoomTypeRequest updateRequest = new RoomTypeRequest(DOUBLE, DOUBLE_DESC, 2, new BigDecimal(PRICE_100));
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomTypeService.updateRoomType(id, hotelId, updateRequest));
    }

    @Test
    void testUpdateRoomTypeBelongsToDifferentHotelReturnsNotFound() {
        final UUID otherHotelId = UUID.randomUUID();
        final RoomTypeRequest updateRequest = new RoomTypeRequest(DOUBLE, DOUBLE_DESC, 2, new BigDecimal(PRICE_100));
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(otherHotelId)))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> roomTypeService.updateRoomType(id, otherHotelId, updateRequest));
    }

    @Test
    void testDeleteRoomTypeSuccess() {
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(roomType));

        roomTypeService.deleteRoomType(id, hotelId);

        verify(roomTypeRepository).delete(Objects.requireNonNull(roomType));
    }

    @Test
    void testDeleteRoomTypeNotFound() {
        when(roomTypeRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomTypeService.deleteRoomType(id, hotelId));
    }
}
