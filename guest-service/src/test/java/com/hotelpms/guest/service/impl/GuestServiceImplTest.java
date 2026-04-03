package com.hotelpms.guest.service.impl;

import com.hotelpms.guest.client.ReservationClient;
import com.hotelpms.guest.dto.request.GuestRequest;
import com.hotelpms.guest.dto.response.GuestResponse;
import com.hotelpms.guest.exception.NotFoundException;
import com.hotelpms.guest.mapper.GuestMapper;
import com.hotelpms.guest.model.Guest;
import com.hotelpms.guest.repository.GuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestServiceImplTest {

    private static final String TEST_FIRST_NAME = "John";
    private static final String TEST_LAST_NAME = "Doe";
    private static final String TEST_EMAIL = "john.doe@example.com";
    private static final String TEST_PHONE = "1234567890";
    private static final String TEST_ADDRESS = "123 Main St";
    private static final String TEST_CITY = "Anytown";
    private static final String TEST_COUNTRY = "Country";

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private GuestMapper guestMapper;

    @Mock
    private ReservationClient reservationClient;

    @InjectMocks
    private GuestServiceImpl guestService;

    private Guest guest;
    private GuestRequest guestRequest;
    private GuestResponse guestResponse;
    private UUID guestId;

    @BeforeEach
    void setUp() {
        guestId = UUID.randomUUID();

        guest = Guest.builder()
                .id(guestId)
                .firstName(TEST_FIRST_NAME)
                .lastName(TEST_LAST_NAME)
                .email(TEST_EMAIL)
                .phone(TEST_PHONE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .active(true)
                .build();

        guestRequest = new GuestRequest(
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_EMAIL,
                TEST_PHONE,
                TEST_ADDRESS,
                TEST_CITY,
                TEST_COUNTRY,
                null);

        guestResponse = new GuestResponse(
                guestId,
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                TEST_EMAIL,
                TEST_PHONE,
                TEST_ADDRESS,
                TEST_CITY,
                TEST_COUNTRY,
                null,
                Collections.emptyList(),
                guest.getCreatedAt(),
                guest.getUpdatedAt());
    }

    @Test
    void shouldCreateGuestSuccessfully() {
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);

        when(guestMapper.toEntity(guestRequest)).thenReturn(nonNullGuest);
        when(guestRepository.save(nonNullGuest)).thenReturn(nonNullGuest);
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final GuestResponse result = guestService.createGuest(guestRequest);

        assertNotNull(result);
        assertEquals(guestId, result.id());
        assertEquals(TEST_FIRST_NAME, result.firstName());
        verify(guestRepository).save(nonNullGuest);
    }

    @Test
    void shouldGetGuestByIdSuccessfully() {
        final UUID nonNullGuestId = Objects.requireNonNull(guestId);
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);

        when(guestRepository.findById(nonNullGuestId)).thenReturn(Optional.of(nonNullGuest));
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final GuestResponse result = guestService.getGuestById(nonNullGuestId);

        assertNotNull(result);
        assertEquals(guestId, result.id());
        assertEquals(TEST_EMAIL, result.email());
    }

    @Test
    void shouldThrowNotFoundExceptionWhenGuestNotFound() {
        final UUID nonNullGuestId = Objects.requireNonNull(guestId);

        when(guestRepository.findById(nonNullGuestId)).thenReturn(Optional.empty());

        final NotFoundException exception = assertThrows(NotFoundException.class,
                () -> guestService.getGuestById(nonNullGuestId));

        assertEquals("GUEST_NOT_FOUND", exception.getMessage());
    }

    @Test
    void shouldDeleteGuestSuccessfully() {
        final UUID nonNullGuestId = Objects.requireNonNull(guestId);
        final Guest nonNullGuest = Objects.requireNonNull(guest);

        when(guestRepository.findById(nonNullGuestId)).thenReturn(Optional.of(nonNullGuest));
        when(reservationClient.hasActiveReservations(nonNullGuestId)).thenReturn(false);

        guestService.deleteGuest(nonNullGuestId);

        verify(guestRepository).delete(nonNullGuest);
    }

    @Test
    void shouldSearchGuestsWithQuery() {
        final String query = "John";
        final Pageable pageable = PageRequest.of(0, 10);
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);
        final Page<Guest> guestPage = new PageImpl<>(Objects.requireNonNull(List.of(nonNullGuest)));

        when(guestRepository.searchByKeyword(query, pageable)).thenReturn(guestPage);
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final Page<GuestResponse> result = guestService.searchGuests(query, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(TEST_FIRST_NAME, result.getContent().get(0).firstName());
    }

    @Test
    void shouldSearchGuestsWithoutQuery() {
        final String query = "";
        final Pageable pageable = PageRequest.of(0, 10);
        final Guest nonNullGuest = Objects.requireNonNull(guest);
        final GuestResponse nonNullGuestResponse = Objects.requireNonNull(guestResponse);
        final Page<Guest> guestPage = new PageImpl<>(Objects.requireNonNull(List.of(nonNullGuest)));

        when(guestRepository.findAll(pageable)).thenReturn(guestPage);
        when(guestMapper.toResponse(nonNullGuest)).thenReturn(nonNullGuestResponse);

        final Page<GuestResponse> result = guestService.searchGuests(query, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(TEST_FIRST_NAME, result.getContent().get(0).firstName());
    }
}
