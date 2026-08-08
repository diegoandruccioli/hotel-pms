package com.hotelpms.frontdesk.pricing.service.impl;

import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.exception.NotFoundException;
import com.hotelpms.frontdesk.pricing.domain.RateSeason;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonRequest;
import com.hotelpms.frontdesk.pricing.dto.RateSeasonResponse;
import com.hotelpms.frontdesk.pricing.mapper.RateSeasonMapper;
import com.hotelpms.frontdesk.pricing.repository.RateSeasonRepository;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateSeasonAdminServiceImplTest {

    private static final String SEASON_NAME = "Alta stagione";
    private static final String PRICE_150 = "150.00";
    private static final LocalDate SEASON_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEASON_END = LocalDate.of(2026, 8, 31);
    /** PostgreSQL SQLState for an EXCLUDE constraint violation. */
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    @Mock
    private RateSeasonRepository rateSeasonRepository;

    @Mock
    private RateSeasonMapper rateSeasonMapper;

    @Mock
    private RoomTypeService roomTypeService;

    @InjectMocks
    private RateSeasonAdminServiceImpl rateSeasonAdminService;

    private UUID id;
    private UUID hotelId;
    private UUID roomTypeId;
    private RateSeason season;
    private RateSeasonRequest request;
    private RateSeasonResponse response;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        hotelId = UUID.randomUUID();
        roomTypeId = UUID.randomUUID();

        season = RateSeason.builder()
                .id(id)
                .hotelId(hotelId)
                .roomTypeId(roomTypeId)
                .name(SEASON_NAME)
                .startDate(SEASON_START)
                .endDate(SEASON_END)
                .nightlyPrice(new BigDecimal(PRICE_150))
                .active(true)
                .build();

        request = new RateSeasonRequest(SEASON_NAME, SEASON_START,
                SEASON_END, new BigDecimal(PRICE_150));

        response = new RateSeasonResponse(id, roomTypeId, SEASON_NAME,
                SEASON_START, SEASON_END, new BigDecimal(PRICE_150));

        lenient().when(roomTypeService.getRoomTypeById(Objects.requireNonNull(roomTypeId),
                Objects.requireNonNull(hotelId))).thenReturn(mockRoomTypeResponse());
    }

    private static RoomTypeResponse mockRoomTypeResponse() {
        return new RoomTypeResponse(UUID.randomUUID(), "Double", "desc", 2,
                new BigDecimal("100.00"), true, null, null);
    }

    @Test
    void listSeasonsReturnsSeasonsForRoomTypeOrderedByStartDate() {
        when(rateSeasonRepository.findAllByRoomTypeIdAndHotelIdOrderByStartDateAsc(
                Objects.requireNonNull(roomTypeId), Objects.requireNonNull(hotelId)))
                .thenReturn(List.of(season));
        when(rateSeasonMapper.toResponse(Objects.requireNonNull(season))).thenReturn(response);

        final List<RateSeasonResponse> result = rateSeasonAdminService.listSeasons(roomTypeId, hotelId);

        assertEquals(1, result.size());
        assertEquals(SEASON_NAME, result.get(0).name());
    }

    @Test
    void listSeasonsForRoomTypeBelongingToDifferentHotelThrowsNotFound() {
        final UUID otherHotelId = UUID.randomUUID();
        when(roomTypeService.getRoomTypeById(Objects.requireNonNull(roomTypeId),
                Objects.requireNonNull(otherHotelId)))
                .thenThrow(new NotFoundException("ROOM_TYPE_NOT_FOUND"));

        assertThrows(NotFoundException.class, () -> rateSeasonAdminService.listSeasons(roomTypeId, otherHotelId));
    }

    @Test
    void createSeasonSuccess() {
        when(rateSeasonMapper.toEntity(Objects.requireNonNull(request))).thenReturn(season);
        when(rateSeasonRepository.saveAndFlush(Objects.requireNonNull(season))).thenReturn(season);
        when(rateSeasonMapper.toResponse(Objects.requireNonNull(season))).thenReturn(response);

        final RateSeasonResponse result = rateSeasonAdminService.createSeason(roomTypeId, hotelId, request);

        assertEquals(SEASON_NAME, result.name());
        assertEquals(hotelId, season.getHotelId());
        assertEquals(roomTypeId, season.getRoomTypeId());
    }

    @Test
    void createSeasonOverlappingAnExistingSeasonThrowsConflict() {
        when(rateSeasonMapper.toEntity(Objects.requireNonNull(request))).thenReturn(season);
        when(rateSeasonRepository.saveAndFlush(Objects.requireNonNull(season)))
                .thenThrow(exclusionViolation());

        assertThrows(ConflictException.class,
                () -> rateSeasonAdminService.createSeason(roomTypeId, hotelId, request));
    }

    @Test
    void createSeasonWithUnrelatedDataIntegrityViolationRethrowsOriginal() {
        final DataIntegrityViolationException notNullViolation =
                new DataIntegrityViolationException("not null violation");
        when(rateSeasonMapper.toEntity(Objects.requireNonNull(request))).thenReturn(season);
        when(rateSeasonRepository.saveAndFlush(Objects.requireNonNull(season))).thenThrow(notNullViolation);

        final DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> rateSeasonAdminService.createSeason(roomTypeId, hotelId, request));
        assertEquals(notNullViolation, thrown);
    }

    @Test
    void updateSeasonSuccess() {
        when(rateSeasonRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(season));
        when(rateSeasonRepository.saveAndFlush(Objects.requireNonNull(season))).thenReturn(season);
        when(rateSeasonMapper.toResponse(Objects.requireNonNull(season))).thenReturn(response);

        final RateSeasonResponse result = rateSeasonAdminService.updateSeason(id, hotelId, request);

        assertEquals(SEASON_NAME, result.name());
        verify(rateSeasonMapper).updateEntityFromRequest(Objects.requireNonNull(request),
                Objects.requireNonNull(season));
    }

    @Test
    void updateSeasonNotFoundThrows() {
        when(rateSeasonRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> rateSeasonAdminService.updateSeason(id, hotelId, request));
    }

    @Test
    void updateSeasonBelongingToDifferentHotelThrowsNotFound() {
        final UUID otherHotelId = UUID.randomUUID();
        when(rateSeasonRepository.findByIdAndHotelId(Objects.requireNonNull(id),
                Objects.requireNonNull(otherHotelId))).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> rateSeasonAdminService.updateSeason(id, otherHotelId, request));
    }

    @Test
    void updateSeasonOverlappingAnotherSeasonThrowsConflict() {
        when(rateSeasonRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(season));
        when(rateSeasonRepository.saveAndFlush(Objects.requireNonNull(season)))
                .thenThrow(exclusionViolation());

        assertThrows(ConflictException.class, () -> rateSeasonAdminService.updateSeason(id, hotelId, request));
    }

    @Test
    void deleteSeasonSuccess() {
        when(rateSeasonRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.of(season));

        rateSeasonAdminService.deleteSeason(id, hotelId);

        verify(rateSeasonRepository).delete(Objects.requireNonNull(season));
    }

    @Test
    void deleteSeasonNotFoundThrows() {
        when(rateSeasonRepository.findByIdAndHotelId(Objects.requireNonNull(id), Objects.requireNonNull(hotelId)))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> rateSeasonAdminService.deleteSeason(id, hotelId));
    }

    private static DataIntegrityViolationException exclusionViolation() {
        final SQLException sqlException = new SQLException("overlap", SQLSTATE_EXCLUSION_VIOLATION);
        return new DataIntegrityViolationException("excl_rate_seasons_no_overlap", sqlException);
    }
}
