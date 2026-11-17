package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.client.NotificationClient;
import com.hotelpms.frontdesk.client.dto.InvoiceStatusResponse;
import com.hotelpms.frontdesk.stays.domain.Stay;
import com.hotelpms.frontdesk.stays.repository.StayRepository;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Check-out must only <em>schedule</em> the email: publishing the event is all the
 * coordinator does inline, none of the slow work (settings, guest, PDF, mail relay).
 */
class StayNotificationCoordinatorTest {

    private static final String PAID_STATUS = "PAID";
    private static final BigDecimal INVOICE_TOTAL = BigDecimal.valueOf(200);

    private final NotificationClient notificationClient = mock(NotificationClient.class);
    private final GuestClient guestClient = mock(GuestClient.class);
    private final BillingClient billingClient = mock(BillingClient.class);
    private final HotelSettingsService hotelSettingsService = mock(HotelSettingsService.class);
    private final StayRepository stayRepository = mock(StayRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final StayNotificationCoordinator coordinator = new StayNotificationCoordinator(
            notificationClient, guestClient, billingClient, hotelSettingsService, stayRepository, eventPublisher);

    @Test
    void shouldOnlyPublishTheCheckoutEventAndDoNoEmailWorkInline() {
        final UUID stayId = UUID.randomUUID();
        final UUID hotelId = UUID.randomUUID();
        final Stay stay = Stay.builder().id(stayId).hotelId(hotelId).build();
        final InvoiceStatusResponse invoice =
                new InvoiceStatusResponse(UUID.randomUUID(), UUID.randomUUID(), PAID_STATUS, INVOICE_TOTAL);

        coordinator.publishCheckoutCompleted(stay, invoice);

        verify(eventPublisher).publishEvent(new StayCheckedOutEvent(stayId, hotelId, invoice));
        verifyNoMoreInteractions(eventPublisher);
        verifyNoInteractions(notificationClient, guestClient, billingClient, hotelSettingsService, stayRepository);
    }
}
