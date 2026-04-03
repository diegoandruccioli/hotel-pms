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
import com.hotelpms.inventory.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link RoomService}.
 *
 * <p>
 * {@code RoomService} defines the extension point via its interface contract;
 * this implementation is a closed leaf. The class is non-{@code final} so that
 * Spring's CGLIB proxy can subclass it for {@code @Transactional} AOP advice.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:DesignForExtension")
public class RoomServiceImpl implements RoomService {

    private static final String ROOM_NOT_FOUND_MSG = "ROOM_NOT_FOUND";
    private static final String TYPE_NOT_FOUND_MSG = "ROOM_TYPE_NOT_FOUND";
    private static final String ROOM_ID_NULL_MSG = "Room ID cannot be null";
    private static final String ROOM_TYPE_ID_NULL_MSG = "RoomType ID cannot be null";

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomMapper roomMapper;

    /**
     * Creates a new room linked to an existing, active {@link RoomType}.
     *
     * @param request the creation request; must not be {@code null}
     * @return the persisted room as a response DTO
     * @throws NotFoundException if the referenced {@code RoomType} does not exist
     *                           or is soft-deleted
     */
    @Override
    @Transactional
    public RoomResponse createRoom(final RoomRequest request) {
        final UUID roomTypeId = Objects.requireNonNull(request.roomTypeId(), ROOM_TYPE_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .filter(RoomType::isActive)
                .orElseThrow(() -> new NotFoundException(TYPE_NOT_FOUND_MSG + roomTypeId));

        final Room room = roomMapper.toEntity(request);
        room.setRoomType(roomType);

        final Room saved = roomRepository.save(room);
        return roomMapper.toResponse(saved);
    }

    /**
     * Retrieves a single active room by its UUID.
     *
     * @param id the room UUID; must not be {@code null}
     * @return the room as a response DTO
     * @throws NotFoundException if no active room exists with the given {@code id}
     */
    @Override
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(final UUID id) {
        Objects.requireNonNull(id, ROOM_ID_NULL_MSG);
        final Room room = roomRepository.findById(id)
                .filter(Room::isActive)
                .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MSG + id));
        return roomMapper.toResponse(room);
    }

    /**
     * Returns a paginated view of all active rooms.
     *
     * <p>
     * {@code Page.map()} delegates LIMIT/OFFSET and COUNT queries to the database,
     * avoiding full in-memory loading.
     *
     * @param pageable pagination and sorting parameters; must not be {@code null}
     * @return a page of room response DTOs
     */
    @Override
    @Transactional(readOnly = true)
    public Page<RoomResponse> getAllRooms(final Pageable pageable) {
        return roomRepository.findAllByActiveTrue(pageable)
                .map(roomMapper::toResponse);
    }

    /**
     * Updates the full details of an existing active room.
     *
     * @param id      the room UUID; must not be {@code null}
     * @param request the update request; must not be {@code null}
     * @return the updated room as a response DTO
     * @throws NotFoundException if the room or the referenced {@code RoomType} is
     *                           not found
     */
    @Override
    @Transactional
    public RoomResponse updateRoom(final UUID id, final RoomRequest request) {
        Objects.requireNonNull(id, ROOM_ID_NULL_MSG);
        final Room room = roomRepository.findById(id)
                .filter(Room::isActive)
                .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MSG + id));

        final UUID roomTypeId = Objects.requireNonNull(request.roomTypeId(), ROOM_TYPE_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .filter(RoomType::isActive)
                .orElseThrow(() -> new NotFoundException(TYPE_NOT_FOUND_MSG + roomTypeId));

        room.setRoomNumber(request.roomNumber());
        room.setStatus(request.status());
        room.setRoomType(roomType);
        room.setHotelId(request.hotelId());

        final Room saved = roomRepository.saveAndFlush(Objects.requireNonNull(room));
        return roomMapper.toResponse(saved);
    }

    /**
     * Updates only the housekeeping status of a room (e.g., {@code DIRTY} after
     * check-out).
     *
     * @param id     the room UUID; must not be {@code null}
     * @param status the new housekeeping status; must not be {@code null}
     * @return the updated room as a response DTO
     * @throws NotFoundException if no active room exists with the given {@code id}
     */
    @Override
    @Transactional
    public RoomResponse updateRoomStatus(final UUID id, final RoomStatus status) {
        Objects.requireNonNull(id, ROOM_ID_NULL_MSG);
        final Room room = roomRepository.findById(id)
                .filter(Room::isActive)
                .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MSG + id));

        room.setStatus(status);

        final Room updated = roomRepository.saveAndFlush(Objects.requireNonNull(room));
        return roomMapper.toResponse(updated);
    }

    /**
     * Soft-deletes a room by delegating to the repository's physical delete,
     * which is intercepted by the {@code @SQLDelete} annotation on the entity
     * to issue an {@code UPDATE active = false} instead.
     *
     * @param id the room UUID; must not be {@code null}
     * @throws NotFoundException if no active room exists with the given {@code id}
     */
    @Override
    @Transactional
    public void deleteRoom(final UUID id) {
        Objects.requireNonNull(id, ROOM_ID_NULL_MSG);
        final Room room = roomRepository.findById(id)
                .filter(Room::isActive)
                .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MSG + id));

        roomRepository.delete(Objects.requireNonNull(room));
    }
}
