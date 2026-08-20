package com.hotelpms.frontdesk.citytax.service.impl;

import com.hotelpms.frontdesk.citytax.domain.HotelCategoryHistory;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryRequest;
import com.hotelpms.frontdesk.citytax.dto.HotelCategoryHistoryResponse;
import com.hotelpms.frontdesk.citytax.mapper.HotelCategoryHistoryMapper;
import com.hotelpms.frontdesk.citytax.repository.HotelCategoryHistoryRepository;
import com.hotelpms.frontdesk.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelCategoryHistoryServiceImplTest {

    private static final String OLD_CATEGORY = "3_STAR";
    private static final String NEW_CATEGORY = "4_STAR";
    private static final LocalDate NEW_CATEGORY_VALID_FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate OLD_RESPONSE_VALID_FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate OLD_ENTRY_VALID_FROM = LocalDate.of(2020, 1, 1);
    /** PostgreSQL SQLState for an EXCLUDE constraint violation. */
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    @Mock
    private HotelCategoryHistoryRepository hotelCategoryHistoryRepository;

    @Mock
    private HotelCategoryHistoryMapper hotelCategoryHistoryMapper;

    @InjectMocks
    private HotelCategoryHistoryServiceImpl hotelCategoryHistoryService;

    private UUID hotelId;
    private HotelCategoryHistoryRequest request;

    @BeforeEach
    void setUp() {
        hotelId = UUID.randomUUID();
        request = new HotelCategoryHistoryRequest(NEW_CATEGORY, NEW_CATEGORY_VALID_FROM);
    }

    @Test
    void listHistoryReturnsMappedEntries() {
        final HotelCategoryHistory entry = HotelCategoryHistory.builder().id(UUID.randomUUID()).hotelId(hotelId).build();
        final HotelCategoryHistoryResponse response =
                new HotelCategoryHistoryResponse(entry.getId(), OLD_CATEGORY, OLD_RESPONSE_VALID_FROM, null);
        when(hotelCategoryHistoryRepository.findAllByHotelIdOrderByValidFromDesc(hotelId)).thenReturn(List.of(entry));
        when(hotelCategoryHistoryMapper.toResponse(entry)).thenReturn(response);

        final List<HotelCategoryHistoryResponse> result = hotelCategoryHistoryService.listHistory(hotelId);

        assertEquals(1, result.size());
    }

    @Test
    void recordCategoryWithNoExistingOpenEntryJustInserts() {
        final HotelCategoryHistory saved = HotelCategoryHistory.builder().id(UUID.randomUUID()).build();
        final HotelCategoryHistoryResponse response =
                new HotelCategoryHistoryResponse(saved.getId(), NEW_CATEGORY, request.validFrom(), null);
        when(hotelCategoryHistoryRepository.findByHotelIdAndValidToIsNull(hotelId)).thenReturn(Optional.empty());
        when(hotelCategoryHistoryRepository.saveAndFlush(any(HotelCategoryHistory.class))).thenReturn(saved);
        when(hotelCategoryHistoryMapper.toResponse(saved)).thenReturn(response);

        final HotelCategoryHistoryResponse result = hotelCategoryHistoryService.recordCategory(hotelId, request);

        assertEquals(response, result);
        verify(hotelCategoryHistoryRepository, never()).save(any());
        final ArgumentCaptor<HotelCategoryHistory> captor = ArgumentCaptor.forClass(HotelCategoryHistory.class);
        verify(hotelCategoryHistoryRepository).saveAndFlush(captor.capture());
        assertEquals(hotelId, captor.getValue().getHotelId());
        assertEquals(NEW_CATEGORY, captor.getValue().getCategory());
    }

    @Test
    void recordCategoryUpgradeClosesExistingOpenEntry() {
        final HotelCategoryHistory openEntry = HotelCategoryHistory.builder()
                .id(UUID.randomUUID()).hotelId(hotelId).category(OLD_CATEGORY)
                .validFrom(OLD_ENTRY_VALID_FROM).build();
        final HotelCategoryHistory saved = HotelCategoryHistory.builder().id(UUID.randomUUID()).build();
        when(hotelCategoryHistoryRepository.findByHotelIdAndValidToIsNull(hotelId)).thenReturn(Optional.of(openEntry));
        when(hotelCategoryHistoryRepository.saveAndFlush(any(HotelCategoryHistory.class))).thenReturn(saved);
        when(hotelCategoryHistoryMapper.toResponse(saved))
                .thenReturn(new HotelCategoryHistoryResponse(saved.getId(), NEW_CATEGORY, request.validFrom(), null));

        hotelCategoryHistoryService.recordCategory(hotelId, request);

        assertEquals(request.validFrom(), openEntry.getValidTo());
        verify(hotelCategoryHistoryRepository).save(openEntry);
    }

    @Test
    void recordCategoryOverlappingThrowsConflict() {
        when(hotelCategoryHistoryRepository.findByHotelIdAndValidToIsNull(hotelId)).thenReturn(Optional.empty());
        when(hotelCategoryHistoryRepository.saveAndFlush(any(HotelCategoryHistory.class)))
                .thenThrow(exclusionViolation());

        assertThrows(ConflictException.class, () -> hotelCategoryHistoryService.recordCategory(hotelId, request));
    }

    @Test
    void recordCategoryWithUnrelatedDataIntegrityViolationRethrowsOriginal() {
        final DataIntegrityViolationException notNullViolation =
                new DataIntegrityViolationException("not null violation");
        when(hotelCategoryHistoryRepository.findByHotelIdAndValidToIsNull(hotelId)).thenReturn(Optional.empty());
        when(hotelCategoryHistoryRepository.saveAndFlush(any(HotelCategoryHistory.class)))
                .thenThrow(notNullViolation);

        final DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> hotelCategoryHistoryService.recordCategory(hotelId, request));
        assertEquals(notNullViolation, thrown);
    }

    private static DataIntegrityViolationException exclusionViolation() {
        final SQLException sqlException = new SQLException("overlap", SQLSTATE_EXCLUSION_VIOLATION);
        return new DataIntegrityViolationException("excl_hotel_category_no_overlap", sqlException);
    }
}
