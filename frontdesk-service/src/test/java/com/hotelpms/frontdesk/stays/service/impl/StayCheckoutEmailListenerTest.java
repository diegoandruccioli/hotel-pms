package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.config.BatchJobContext;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StayCheckoutEmailListenerTest {

    private static final String PAID_STATUS = "PAID";

    private final StayRepository stayRepository = mock(StayRepository.class);
    private final StayNotificationCoordinator coordinator = mock(StayNotificationCoordinator.class);
    private final StayCheckoutEmailListener listener = new StayCheckoutEmailListener(stayRepository, coordinator);

    private final UUID stayId = UUID.randomUUID();
    private final UUID hotelId = UUID.randomUUID();
    private final Stay stay = Stay.builder().id(stayId).hotelId(hotelId).build();
    private final InvoiceStatusResponse invoice =
            new InvoiceStatusResponse(UUID.randomUUID(), UUID.randomUUID(), PAID_STATUS, BigDecimal.TEN);
    private final StayCheckedOutEvent event = new StayCheckedOutEvent(stayId, hotelId, invoice);

    @Test
    void shouldReReadTheStayAndSendTheEmailWithTheHotelContextBound() {
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        final AtomicReference<BatchJobContext> seen = new AtomicReference<>();
        doAnswer(call -> {
            seen.set(BatchJobContext.get());
            return null;
        }).when(coordinator).sendCheckoutEmailIfPossible(stay, invoice);

        listener.onStayCheckedOut(event);

        verify(coordinator).sendCheckoutEmailIfPossible(stay, invoice);
        assertEquals(hotelId.toString(), seen.get().getHotelId());
        assertNull(BatchJobContext.get(), "the batch context must not leak onto the pool thread");
    }

    @Test
    void shouldDoNothingWhenTheStayNoLongerExists() {
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.empty());

        listener.onStayCheckedOut(event);

        verify(coordinator, never()).sendCheckoutEmailIfPossible(ArgumentMatchers.any(), ArgumentMatchers.any());
        assertNull(BatchJobContext.get());
    }

    @Test
    void shouldSwallowAnUnexpectedErrorMarkTheEmailFailedAndStillClearTheContext() {
        when(stayRepository.findByIdAndHotelId(stayId, hotelId)).thenReturn(Optional.of(stay));
        doThrow(new DataAccessResourceFailureException("db down")).when(coordinator).sendCheckoutEmailIfPossible(stay, invoice);

        listener.onStayCheckedOut(event);

        assertTrue(stay.isCheckoutEmailFailed());
        verify(stayRepository).save(stay);
        assertNull(BatchJobContext.get());
    }
}
