package com.hotelpms.frontdesk.rooms.service.impl;

import com.hotelpms.frontdesk.rooms.domain.RoomType;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.rooms.mapper.RoomTypeMapper;
import com.hotelpms.frontdesk.rooms.repository.RoomTypeRepository;
import com.hotelpms.frontdesk.rooms.service.RoomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of RoomTypeService.
 *
 * <p>
 * RoomTypes are static reference data that changes infrequently, scoped per
 * hotel (T-ROOM-02). All read methods are cached under the {@code "roomTypes"}
 * cache, keyed by hotelId so one hotel's catalog is never served from another
 * hotel's cache entry. Any write operation (create, update, delete) evicts the
 * entire {@code "roomTypes"} cache to guarantee consistency.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:DesignForExtension")
public class RoomTypeServiceImpl implements RoomTypeService {

    private static final String CACHE_NAME = "roomTypes";
    private static final String NOT_FOUND_MSG = "ROOM_TYPE_NOT_FOUND";
    private static final String ROOM_TYPE_ID_NULL_MSG = "RoomType ID cannot be null";
    private static final String HOTEL_ID_NULL_MSG = "Hotel ID cannot be null";

    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeMapper roomTypeMapper;

    /**
     * Creates a room type scoped to the caller's hotel and evicts the entire
     * cache to keep reads consistent.
     *
     * @param request the request
     * @param hotelId the authenticated hotel UUID
     * @return the room type response
     */
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public RoomTypeResponse createRoomType(final RoomTypeRequest request, final UUID hotelId) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final RoomType roomType = roomTypeMapper.toEntity(request);
        roomType.setHotelId(hotelId);
        final RoomType saved = roomTypeRepository.saveAndFlush(Objects.requireNonNull(roomType));
        return roomTypeMapper.toResponse(saved);
    }

    /**
     * Gets a room type by id, scoped to the caller's hotel, served from the
     * {@code "roomTypes"} cache after the first DB hit.
     *
     * @param id      the id
     * @param hotelId the authenticated hotel UUID; the room type must belong to it
     * @return the room type response
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#hotelId + ':' + #id")
    public RoomTypeResponse getRoomTypeById(final UUID id, final UUID hotelId) {
        Objects.requireNonNull(id, ROOM_TYPE_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findByIdAndHotelId(id, hotelId)
                .filter((@NonNull RoomType rt) -> rt.isActive())
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG + id));
        return roomTypeMapper.toResponse(roomType);
    }

    /**
     * Gets all room types belonging to the caller's hotel. The full list is
     * cached under a per-hotel key so that repeated calls (e.g., on every
     * reservation creation) do not hit the database.
     *
     * @param hotelId the authenticated hotel UUID
     * @return a list of room type responses scoped to the hotel
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#hotelId")
    public List<RoomTypeResponse> getAllRoomTypes(final UUID hotelId) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        return roomTypeRepository.findAllByHotelId(hotelId).stream()
                .filter((@NonNull RoomType rt) -> rt.isActive())
                .map(roomTypeMapper::toResponse)
                .toList();
    }

    /**
     * Updates a room type scoped to the caller's hotel and evicts the entire
     * cache (both the {@code "all"} key and the per-ID entry) so stale data is
     * never served after a change.
     *
     * @param id      the id
     * @param hotelId the authenticated hotel UUID; the room type must belong to it
     * @param request the request
     * @return the room type response
     */
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public RoomTypeResponse updateRoomType(final UUID id, final UUID hotelId, final RoomTypeRequest request) {
        Objects.requireNonNull(id, ROOM_TYPE_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findByIdAndHotelId(id, hotelId)
                .filter((@NonNull RoomType rt) -> rt.isActive())
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG + id));

        roomType.setName(request.name());
        roomType.setDescription(request.description());
        roomType.setMaxOccupancy(request.maxOccupancy());
        roomType.setBasePrice(request.basePrice());

        final RoomType updated = roomTypeRepository.saveAndFlush(Objects.requireNonNull(roomType));
        return roomTypeMapper.toResponse(updated);
    }

    /**
     * Soft-deletes a room type scoped to the caller's hotel and evicts the
     * entire cache to reflect the removal.
     *
     * @param id      the id
     * @param hotelId the authenticated hotel UUID; the room type must belong to it
     */
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteRoomType(final UUID id, final UUID hotelId) {
        Objects.requireNonNull(id, ROOM_TYPE_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findByIdAndHotelId(id, hotelId)
                .filter((@NonNull RoomType rt) -> rt.isActive())
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG + id));

        roomTypeRepository.delete(Objects.requireNonNull(roomType)); // Triggers the @SQLDelete soft delete
    }
}
