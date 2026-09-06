package com.hotelpms.frontdesk.nightaudit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.dto.PaymentMethodTotalDto;
import com.hotelpms.frontdesk.client.dto.PaymentSummaryClientResponse;
import com.hotelpms.frontdesk.dashboard.dto.DaySheetResponse;
import com.hotelpms.frontdesk.dashboard.service.DaySheetService;
import com.hotelpms.frontdesk.exception.BadRequestException;
import com.hotelpms.frontdesk.exception.ConflictException;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditRun;
import com.hotelpms.frontdesk.nightaudit.domain.NightAuditStatus;
import com.hotelpms.frontdesk.nightaudit.dto.NightAuditRunResponse;
import com.hotelpms.frontdesk.nightaudit.repository.NightAuditRunRepository;
import com.hotelpms.frontdesk.reservations.domain.Reservation;
import com.hotelpms.frontdesk.reservations.domain.ReservationStatus;
import com.hotelpms.frontdesk.reservations.repository.ReservationRepository;
import com.hotelpms.frontdesk.reservations.service.ReservationService;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NightAuditServiceImplTest {

    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 6, 15);
    private static final String RUN_BY = "admin";
    private static final int ARRIVALS = 3;
    private static final int DEPARTURES = 2;
    private static final long GUESTS_IN_HOUSE = 5L;
    private static final long CURRENT_STAYS = 4L;
    private static final int AVAILABLE_ROOMS = 10;
    private static final BigDecimal CASH_TOTAL = BigDecimal.valueOf(150);

    @Mock
    private NightAuditRunRepository nightAuditRunRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationService reservationService;
    @Mock
    private DaySheetService daySheetService;
    @Mock
    private BillingClient billingClient;
    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    private NightAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NightAuditServiceImpl(nightAuditRunRepository, reservationRepository, reservationService,
                daySheetService, billingClient, new ObjectMapper(), hotelSettingsRepository);

        final UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(RUN_BY, "", List.of());
        auth.setDetails(HOTEL_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completesWithNoCandidatesAndHealthyCashSummary() {
        when(nightAuditRunRepository.findByHotelIdAndBusinessDate(HOTEL_ID, BUSINESS_DATE))
                .thenReturn(Optional.empty());
        when(nightAuditRunRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.findByHotelIdAndCheckInDateLessThanEqualAndStatusIn(
                eq(HOTEL_ID), eq(BUSINESS_DATE), anyList())).thenReturn(List.of());
        when(daySheetService.getDaySheet(BUSINESS_DATE, HOTEL_ID)).thenReturn(sampleDaySheet());
        when(billingClient.getPaymentSummary(BUSINESS_DATE)).thenReturn(new PaymentSummaryClientResponse(
                BUSINESS_DATE, List.of(new PaymentMethodTotalDto("CASH", CASH_TOTAL)), CASH_TOTAL));

        final NightAuditRunResponse result = service.run(BUSINESS_DATE, RUN_BY);

        assertEquals(NightAuditStatus.COMPLETED, result.status());
        assertEquals(ARRIVALS, result.arrivals());
        assertEquals(DEPARTURES, result.departures());
        assertEquals(0, result.noShowsMarked());
        assertFalse(result.cashSummaryDegraded());
        assertEquals(1, result.cashByMethod().size());
        assertEquals(0, CASH_TOTAL.compareTo(result.cashByMethod().get(0).total()));
        verify(reservationService, never()).updateStatusAndGuestsForHotel(any(), any(), any(), any(), any());
    }

    @Test
    void marksEligibleCandidatesAsNoShowAndSkipsRejectedOnes() {
        final Reservation eligible = reservationWithId();
        final Reservation rejected = reservationWithId();
        when(nightAuditRunRepository.findByHotelIdAndBusinessDate(HOTEL_ID, BUSINESS_DATE))
                .thenReturn(Optional.empty());
        when(nightAuditRunRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.findByHotelIdAndCheckInDateLessThanEqualAndStatusIn(
                eq(HOTEL_ID), eq(BUSINESS_DATE), anyList())).thenReturn(List.of(eligible, rejected));
        when(reservationService.updateStatusAndGuestsForHotel(
                HOTEL_ID, eligible.getId(), ReservationStatus.NO_SHOW, null, null))
                .thenReturn(null);
        when(reservationService.updateStatusAndGuestsForHotel(
                HOTEL_ID, rejected.getId(), ReservationStatus.NO_SHOW, null, null))
                .thenThrow(new ConflictException("RESERVATION_NO_SHOW_HAS_STAY"));
        when(daySheetService.getDaySheet(BUSINESS_DATE, HOTEL_ID)).thenReturn(sampleDaySheet());
        when(billingClient.getPaymentSummary(BUSINESS_DATE))
                .thenReturn(new PaymentSummaryClientResponse(BUSINESS_DATE, List.of(), BigDecimal.ZERO));

        final NightAuditRunResponse result = service.run(BUSINESS_DATE, RUN_BY);

        assertEquals(NightAuditStatus.COMPLETED, result.status());
        assertEquals(1, result.noShowsMarked());
        verify(reservationService).updateStatusAndGuestsForHotel(
                HOTEL_ID, eligible.getId(), ReservationStatus.NO_SHOW, null, null);
        verify(reservationService).updateStatusAndGuestsForHotel(
                HOTEL_ID, rejected.getId(), ReservationStatus.NO_SHOW, null, null);
    }

    @Test
    void rejectsRunningForABusinessDateInTheFuture() {
        assertThrows(BadRequestException.class,
                () -> service.run(LocalDate.now().plusDays(1), RUN_BY));

        verify(nightAuditRunRepository, never()).findByHotelIdAndBusinessDate(any(), any());
        verify(nightAuditRunRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRunningAgainForAnAlreadyCompletedDate() {
        final NightAuditRun completed = NightAuditRun.builder()
                .hotelId(HOTEL_ID).businessDate(BUSINESS_DATE).status(NightAuditStatus.COMPLETED).build();
        when(nightAuditRunRepository.findByHotelIdAndBusinessDate(HOTEL_ID, BUSINESS_DATE))
                .thenReturn(Optional.of(completed));

        assertThrows(ConflictException.class, () -> service.run(BUSINESS_DATE, RUN_BY));

        verify(nightAuditRunRepository, never()).saveAndFlush(any());
        verify(nightAuditRunRepository, never()).delete(any());
    }

    @Test
    void deletesAPriorFailedRowAndRetries() {
        final NightAuditRun failed = NightAuditRun.builder()
                .hotelId(HOTEL_ID).businessDate(BUSINESS_DATE).status(NightAuditStatus.FAILED).build();
        when(nightAuditRunRepository.findByHotelIdAndBusinessDate(HOTEL_ID, BUSINESS_DATE))
                .thenReturn(Optional.of(failed));
        when(nightAuditRunRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.findByHotelIdAndCheckInDateLessThanEqualAndStatusIn(
                eq(HOTEL_ID), eq(BUSINESS_DATE), anyList())).thenReturn(List.of());
        when(daySheetService.getDaySheet(BUSINESS_DATE, HOTEL_ID)).thenReturn(sampleDaySheet());
        when(billingClient.getPaymentSummary(BUSINESS_DATE))
                .thenReturn(new PaymentSummaryClientResponse(BUSINESS_DATE, List.of(), BigDecimal.ZERO));

        final NightAuditRunResponse result = service.run(BUSINESS_DATE, RUN_BY);

        verify(nightAuditRunRepository).delete(failed);
        assertEquals(NightAuditStatus.COMPLETED, result.status());
    }

    @Test
    void recordsAFailedRowWhenTheAuditWorkThrows() {
        when(nightAuditRunRepository.findByHotelIdAndBusinessDate(HOTEL_ID, BUSINESS_DATE))
                .thenReturn(Optional.empty());
        when(nightAuditRunRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.findByHotelIdAndCheckInDateLessThanEqualAndStatusIn(
                eq(HOTEL_ID), eq(BUSINESS_DATE), anyList())).thenReturn(List.of());
        when(daySheetService.getDaySheet(BUSINESS_DATE, HOTEL_ID))
                .thenThrow(new org.springframework.dao.DataRetrievalFailureException("DAY_SHEET_BOOM"));

        final NightAuditRunResponse result = service.run(BUSINESS_DATE, RUN_BY);

        assertEquals(NightAuditStatus.FAILED, result.status());
        assertEquals("DAY_SHEET_BOOM", result.failureReason());
        // saveAndFlush is called twice: once to persist the RUNNING claim, once to
        // persist the FAILED outcome — both calls pass the SAME mutable entity
        // reference (mutated in place between calls), so asserting on captured
        // argument *state* after the fact would just see the final FAILED state
        // twice; call *count* is the meaningful assertion here.
        verify(nightAuditRunRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void marksCashSummaryDegradedWhenBillingServiceIsUnreachable() {
        when(nightAuditRunRepository.findByHotelIdAndBusinessDate(HOTEL_ID, BUSINESS_DATE))
                .thenReturn(Optional.empty());
        when(nightAuditRunRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.findByHotelIdAndCheckInDateLessThanEqualAndStatusIn(
                eq(HOTEL_ID), eq(BUSINESS_DATE), anyList())).thenReturn(List.of());
        when(daySheetService.getDaySheet(BUSINESS_DATE, HOTEL_ID)).thenReturn(sampleDaySheet());
        // Mirrors BillingClient.getPaymentSummaryFallback: null grandTotal is the degraded sentinel.
        when(billingClient.getPaymentSummary(BUSINESS_DATE))
                .thenReturn(new PaymentSummaryClientResponse(BUSINESS_DATE, List.of(), null));

        final NightAuditRunResponse result = service.run(BUSINESS_DATE, RUN_BY);

        assertEquals(NightAuditStatus.COMPLETED, result.status());
        assertTrue(result.cashSummaryDegraded());
    }

    @Test
    void historyIsDelegatedToRepositoryScopedByHotel() {
        final NightAuditRun run = NightAuditRun.builder()
                .id(UUID.randomUUID()).hotelId(HOTEL_ID).businessDate(BUSINESS_DATE)
                .status(NightAuditStatus.COMPLETED).runBy(RUN_BY).build();
        when(nightAuditRunRepository.findByHotelIdOrderByBusinessDateDesc(eq(HOTEL_ID), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(run)));

        final var page = service.getHistory(PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals(BUSINESS_DATE, page.getContent().get(0).businessDate());
    }

    private static Reservation reservationWithId() {
        final Reservation reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(UUID.randomUUID());
        return reservation;
    }

    private static DaySheetResponse sampleDaySheet() {
        return new DaySheetResponse(BUSINESS_DATE, ARRIVALS, DEPARTURES, GUESTS_IN_HOUSE, CURRENT_STAYS,
                AVAILABLE_ROOMS, java.util.Map.of());
    }
}
