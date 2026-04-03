package com.hotelpms.inventory.service.impl;

import com.hotelpms.inventory.domain.RoomType;
import com.hotelpms.inventory.dto.RoomTypeRequest;
import com.hotelpms.inventory.dto.RoomTypeResponse;
import com.hotelpms.inventory.exception.NotFoundException;
import com.hotelpms.inventory.mapper.RoomTypeMapper;
import com.hotelpms.inventory.repository.RoomTypeRepository;
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

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        roomType = RoomType.builder()
                .id(id)
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
        when(roomTypeRepository.save(Objects.requireNonNull(roomType))).thenReturn(roomType);
        when(roomTypeMapper.toResponse(Objects.requireNonNull(roomType))).thenReturn(response);

        final RoomTypeResponse result = roomTypeService.createRoomType(request);

        assertNotNull(result);
        assertEquals(SINGLE, result.name());
        verify(roomTypeRepository).save(Objects.requireNonNull(roomType));
    }

    @Test
    void testGetRoomTypeByIdSuccess() {
        when(roomTypeRepository.findById(Objects.requireNonNull(id))).thenReturn(Optional.of(roomType));
        when(roomTypeMapper.toResponse(Objects.requireNonNull(roomType))).thenReturn(response);

        final RoomTypeResponse result = roomTypeService.getRoomTypeById(id);

        assertNotNull(result);
        assertEquals(id, result.id());
    }

    @Test
    void testGetRoomTypeByIdNotFound() {
        when(roomTypeRepository.findById(Objects.requireNonNull(id))).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomTypeService.getRoomTypeById(id));
    }

    @Test
    void testGetAllRoomTypesSuccess() {
        when(roomTypeRepository.findAll()).thenReturn(List.of(roomType));
        when(roomTypeMapper.toResponse(Objects.requireNonNull(roomType))).thenReturn(response);

        final List<RoomTypeResponse> result = roomTypeService.getAllRoomTypes();

        assertEquals(1, result.size());
        assertEquals(SINGLE, result.get(0).name());
    }

    @Test
    void testUpdateRoomTypeSuccess() {
        final RoomTypeRequest updateRequest = new RoomTypeRequest(DOUBLE, DOUBLE_DESC, 2, new BigDecimal(PRICE_100));
        final RoomType updatedRoomType = RoomType.builder()
                .id(id)
                .name(DOUBLE)
                .active(true)
                .build();
        final RoomTypeResponse updateResponse = new RoomTypeResponse(id, DOUBLE, DOUBLE_DESC, 2,
                new BigDecimal(PRICE_100), true, null, null);

        when(roomTypeRepository.findById(Objects.requireNonNull(id))).thenReturn(Optional.of(roomType));
        when(roomTypeRepository.save(Objects.requireNonNull(roomType))).thenReturn(updatedRoomType);
        when(roomTypeMapper.toResponse(Objects.requireNonNull(updatedRoomType))).thenReturn(updateResponse);

        final RoomTypeResponse result = roomTypeService.updateRoomType(id, updateRequest);

        assertEquals(DOUBLE, result.name());
        verify(roomTypeRepository).save(Objects.requireNonNull(roomType)); // The active roomType is updated
    }

    @Test
    void testUpdateRoomTypeNotFound() {
        final RoomTypeRequest updateRequest = new RoomTypeRequest(DOUBLE, DOUBLE_DESC, 2, new BigDecimal(PRICE_100));
        when(roomTypeRepository.findById(Objects.requireNonNull(id))).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomTypeService.updateRoomType(id, updateRequest));
    }

    @Test
    void testDeleteRoomTypeSuccess() {
        when(roomTypeRepository.findById(Objects.requireNonNull(id))).thenReturn(Optional.of(roomType));

        roomTypeService.deleteRoomType(id);

        verify(roomTypeRepository).delete(Objects.requireNonNull(roomType));
    }

    @Test
    void testDeleteRoomTypeNotFound() {
        when(roomTypeRepository.findById(Objects.requireNonNull(id))).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> roomTypeService.deleteRoomType(id));
    }
}
