package com.hotelpms.frontdesk.rooms.service.impl;

import com.hotelpms.frontdesk.rooms.domain.Room;
import com.hotelpms.frontdesk.rooms.domain.RoomStatus;
import com.hotelpms.frontdesk.rooms.domain.RoomType;
import com.hotelpms.frontdesk.rooms.dto.RoomRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomResponse;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.rooms.mapper.RoomMapper;
import com.hotelpms.frontdesk.rooms.repository.RoomRepository;
import com.hotelpms.frontdesk.rooms.repository.RoomTypeRepository;
import com.hotelpms.frontdesk.rooms.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private static final String HOTEL_ID_NULL_MSG = "Hotel ID cannot be null";

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomMapper roomMapper;

    /**
     * Creates a new room linked to an existing, active {@link RoomType},
     * scoped to the authenticated hotel.
     *
     * @param request the creation request; must not be {@code null}
     * @param hotelId the hotel UUID from the authenticated user's JWT; always
     *                wins over any {@code hotelId} present in the request body
     *                (T-ROOM-01)
     * @return the persisted room as a response DTO
     * @throws NotFoundException if the referenced {@code RoomType} does not exist
     *                           or is soft-deleted
     */
    @Override
    @Transactional
    public RoomResponse createRoom(final RoomRequest request, final UUID hotelId) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final UUID roomTypeId = Objects.requireNonNull(request.roomTypeId(), ROOM_TYPE_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .filter(RoomType::isActive)
                .orElseThrow(() -> new NotFoundException(TYPE_NOT_FOUND_MSG + roomTypeId));

        final Room room = roomMapper.toEntity(request);
        room.setHotelId(hotelId);
        room.setRoomType(roomType);

        final Room saved = roomRepository.save(room);
        return roomMapper.toResponse(saved);
    }

    /**
     * Retrieves a single active room by its UUID, scoped to the authenticated hotel.
     *
     * @param id      the room UUID; must not be {@code null}
     * @param hotelId the hotel UUID; must not be {@code null}
     * @return the room as a response DTO
     * @throws NotFoundException if no active room exists for the given {@code id} and {@code hotelId}
     */
    @Override
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(final UUID id, final UUID hotelId) {
        Objects.requireNonNull(id, ROOM_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final Room room = roomRepository.findByIdAndActiveTrueAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MSG + id));
        return roomMapper.toResponse(room);
    }

    /**
     * Returns a paginated view of all active rooms belonging to the
     * authenticated hotel (T-ROOM-01).
     *
     * <p>
     * {@code Page.map()} delegates LIMIT/OFFSET and COUNT queries to the database,
     * avoiding full in-memory loading.
     *
     * @param pageable pagination and sorting parameters; must not be {@code null}
     * @param hotelId  the hotel UUID from the authenticated user's JWT
     * @return a page of room response DTOs
     */
    @Override
    @Transactional(readOnly = true)
    public Page<RoomResponse> getAllRooms(final Pageable pageable, final UUID hotelId) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        return roomRepository.findAllByActiveTrueAndHotelId(hotelId, pageable)
                .map(roomMapper::toResponse);
    }

    /**
     * Updates the full details of an existing active room, scoped to the authenticated hotel.
     *
     * @param id      the room UUID; must not be {@code null}
     * @param hotelId the hotel UUID; must not be {@code null}
     * @param request the update request; must not be {@code null}
     * @return the updated room as a response DTO
     * @throws NotFoundException if the room or the referenced {@code RoomType} is
     *                           not found
     */
    @Override
    @Transactional
    public RoomResponse updateRoom(final UUID id, final UUID hotelId, final RoomRequest request) {
        Objects.requireNonNull(id, ROOM_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final Room room = roomRepository.findByIdAndActiveTrueAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MSG + id));

        final UUID roomTypeId = Objects.requireNonNull(request.roomTypeId(), ROOM_TYPE_ID_NULL_MSG);
        final RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .filter(RoomType::isActive)
                .orElseThrow(() -> new NotFoundException(TYPE_NOT_FOUND_MSG + roomTypeId));

        room.setRoomNumber(request.roomNumber());
        room.setStatus(request.status());
        room.setRoomType(roomType);
        // hotelId always comes from the authenticated context (T-ROOM-01), never
        // from the request body — otherwise a caller could move a room to a
        // different hotel by simply changing this field.
        room.setHotelId(hotelId);

        final Room saved = roomRepository.saveAndFlush(Objects.requireNonNull(room));
        return roomMapper.toResponse(saved);
    }

    /**
     * Updates only the housekeeping status of a room (e.g., {@code DIRTY} after
     * check-out), scoped to the authenticated hotel.
     *
     * @param id      the room UUID; must not be {@code null}
     * @param hotelId the hotel UUID; must not be {@code null}
     * @param status  the new housekeeping status; must not be {@code null}
     * @return the updated room as a response DTO
     * @throws NotFoundException if no active room exists with the given {@code id} and {@code hotelId}
     */
    @Override
    @Transactional
    public RoomResponse updateRoomStatus(final UUID id, final UUID hotelId, final RoomStatus status) {
        Objects.requireNonNull(id, ROOM_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final Room room = roomRepository.findByIdAndActiveTrueAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MSG + id));

        room.setStatus(status);

        final Room updated = roomRepository.saveAndFlush(Objects.requireNonNull(room));
        return roomMapper.toResponse(updated);
    }

    /**
     * Soft-deletes a room by delegating to the repository's physical delete,
     * which is intercepted by the {@code @SQLDelete} annotation on the entity
     * to issue an {@code UPDATE active = false} instead.
     * Scoped to the authenticated hotel to prevent cross-hotel deletion.
     *
     * @param id      the room UUID; must not be {@code null}
     * @param hotelId the hotel UUID; must not be {@code null}
     * @throws NotFoundException if no active room exists with the given {@code id} and {@code hotelId}
     */
    @Override
    @Transactional
    public void deleteRoom(final UUID id, final UUID hotelId) {
        Objects.requireNonNull(id, ROOM_ID_NULL_MSG);
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        final Room room = roomRepository.findByIdAndActiveTrueAndHotelId(id, hotelId)
                .orElseThrow(() -> new NotFoundException(ROOM_NOT_FOUND_MSG + id));

        roomRepository.delete(Objects.requireNonNull(room));
    }

    /**
     * Returns every active, {@code CLEAN} room belonging to the authenticated
     * hotel, unpaginated.
     *
     * @param hotelId the hotel UUID from the authenticated user's JWT; must not be {@code null}
     * @return the clean rooms for that hotel, mapped to response DTOs
     */
    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> findCleanRooms(final UUID hotelId) {
        Objects.requireNonNull(hotelId, HOTEL_ID_NULL_MSG);
        return roomRepository.findAllByActiveTrueAndHotelIdAndStatus(hotelId, RoomStatus.CLEAN).stream()
                .map(roomMapper::toResponse)
                .toList();
    }
}
