package com.hotelpms.inventory.service.impl;

import com.hotelpms.inventory.domain.RoomType;
import com.hotelpms.inventory.dto.RoomTypeRequest;
import com.hotelpms.inventory.dto.RoomTypeResponse;
import com.hotelpms.inventory.exception.NotFoundException;
import com.hotelpms.inventory.mapper.RoomTypeMapper;
import com.hotelpms.inventory.repository.RoomTypeRepository;
import com.hotelpms.inventory.service.RoomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of RoomTypeService.
 *
 * <p>
 * RoomTypes are static reference data that changes infrequently. All read
 * methods are
 * cached under the {@code "roomTypes"} cache. Any write operation (create,
 * update, delete)
 * evicts the entire {@code "roomTypes"} cache to guarantee consistency.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:DesignForExtension")
public class RoomTypeServiceImpl implements RoomTypeService {

    private static final String CACHE_NAME = "roomTypes";
    private static final String NOT_FOUND_MSG = "ROOM_TYPE_NOT_FOUND";
    private static final String ROOM_TYPE_ID_NULL_MSG = "RoomType ID cannot be null";

    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeMapper roomTypeMapper;

    /**
     * Creates a room type and evicts the entire cache to keep reads consistent.
     *
     * @param request the request
     * @return the room type response
     */
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public RoomTypeResponse createRoomType(final RoomTypeRequest request) {
        final RoomType roomType = roomTypeMapper.toEntity(request);
        final RoomType saved = roomTypeRepository.saveAndFlush(Objects.requireNonNull(roomType));
        return roomTypeMapper.toResponse(saved);
    }

    /**
     * Gets a room type by id, served from the {@code "roomTypes"} cache after the
     * first DB hit.
     * The cache key is the UUID string representation of the {@code id} parameter.
     *
     * @param id the id
     * @return the room type response
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#id")
    public RoomTypeResponse getRoomTypeById(final UUID id) {
        Objects.requireNonNull(id, ROOM_TYPE_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findById(id)
                .filter(RoomType::isActive)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG + id));
        return roomTypeMapper.toResponse(roomType);
    }

    /**
     * Gets all room types. The full list is cached under the key {@code "all"} so
     * that
     * repeated calls (e.g., on every reservation creation) do not hit the database.
     *
     * @return a list of room type responses
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'all'")
    public List<RoomTypeResponse> getAllRoomTypes() {
        return roomTypeRepository.findAll().stream()
                .filter(RoomType::isActive)
                .map(roomTypeMapper::toResponse)
                .toList();
    }

    /**
     * Updates a room type and evicts the entire cache (both the {@code "all"} key
     * and the
     * per-ID entry) so stale data is never served after a change.
     *
     * @param id      the id
     * @param request the request
     * @return the room type response
     */
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public RoomTypeResponse updateRoomType(final UUID id, final RoomTypeRequest request) {
        Objects.requireNonNull(id, ROOM_TYPE_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findById(id)
                .filter(RoomType::isActive)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG + id));

        roomType.setName(request.name());
        roomType.setDescription(request.description());
        roomType.setMaxOccupancy(request.maxOccupancy());
        roomType.setBasePrice(request.basePrice());

        final RoomType updated = roomTypeRepository.saveAndFlush(Objects.requireNonNull(roomType));
        return roomTypeMapper.toResponse(updated);
    }

    /**
     * Soft-deletes a room type and evicts the entire cache to reflect the removal.
     *
     * @param id the id
     */
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteRoomType(final UUID id) {
        Objects.requireNonNull(id, ROOM_TYPE_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findById(id)
                .filter(RoomType::isActive)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MSG + id));

        roomTypeRepository.delete(Objects.requireNonNull(roomType)); // Triggers the @SQLDelete soft delete
    }
}
