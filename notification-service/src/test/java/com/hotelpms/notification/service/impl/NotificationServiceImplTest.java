package com.hotelpms.notification.service.impl;

import com.hotelpms.notification.dto.CheckinNotificationRequest;
import com.hotelpms.notification.dto.CheckoutNotificationRequest;
import com.hotelpms.notification.dto.InvoiceLineItemDto;
import com.hotelpms.notification.dto.ReservationConfirmedRequest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String FROM_ADDRESS = "noreply@test.com";
    private static final String FROM_NAME = "Test Hotel";
    private static final String GUEST_EMAIL = "guest@example.com";
    private static final String GUEST_NAME = "Jane Doe";
    private static final String HOTEL_NAME = "Test Hotel";
    private static final String HTML_BODY = "<html><body>test</body></html>";
    private static final String LOCALE_IT = "it";
    private static final String LOCALE_EN = "en";
    private static final String ROOM_GENERIC = "Room";
    private static final String CURRENCY_EUR = "EUR";
    private static final String ROOM_NUMBER_101 = "101";
    private static final String SUBJECT_READ_ERROR = "Could not read subject";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    private NotificationServiceImpl service;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(mailSender, templateEngine, FROM_ADDRESS, FROM_NAME);
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(IContext.class))).thenReturn(HTML_BODY);
    }

    @Test
    void sendReservationConfirmedCallsCorrectTemplate() {
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, "Superior Room",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), 4, "RES-001", LOCALE_IT,
                null, null, null);

        service.sendReservationConfirmed(req);

        verify(templateEngine).process(contains("reservation-confirmed-it"), any(IContext.class));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendReservationConfirmedEnglishLocaleUsesEnTemplate() {
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, "Deluxe Room",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), 2, "RES-002", LOCALE_EN,
                null, null, null);

        service.sendReservationConfirmed(req);

        verify(templateEngine).process(contains("reservation-confirmed-en"), any(IContext.class));
    }

    @Test
    void sendCheckinCallsCorrectTemplate() {
        final CheckinNotificationRequest req = new CheckinNotificationRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, ROOM_NUMBER_101,
                LocalDate.of(2026, 8, 5), LOCALE_IT);

        service.sendCheckin(req);

        verify(templateEngine).process(contains("checkin-it"), any(IContext.class));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendCheckoutCallsCorrectTemplate() {
        final CheckoutNotificationRequest req = new CheckoutNotificationRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, ROOM_NUMBER_101,
                LocalDateTime.of(2026, 8, 1, 14, 0),
                LocalDateTime.of(2026, 8, 5, 11, 0),
                List.of(new InvoiceLineItemDto("Room charge", BigDecimal.valueOf(400))),
                BigDecimal.valueOf(400), CURRENCY_EUR, LOCALE_IT,
                null, null, null);

        service.sendCheckout(req);

        verify(templateEngine).process(contains("checkout-it"), any(IContext.class));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendCheckoutEnglishLocaleUsesEnTemplate() {
        final CheckoutNotificationRequest req = new CheckoutNotificationRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, "202",
                LocalDateTime.of(2026, 8, 1, 14, 0),
                LocalDateTime.of(2026, 8, 5, 11, 0),
                List.of(), BigDecimal.valueOf(200), CURRENCY_EUR, LOCALE_EN,
                null, null, null);

        service.sendCheckout(req);

        verify(templateEngine).process(contains("checkout-en"), any(IContext.class));
    }

    @Test
    void unknownLocaleFallsBackToItalianTemplate() {
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, ROOM_GENERIC,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, "RES-003", "fr",
                null, null, null);

        service.sendReservationConfirmed(req);

        verify(templateEngine).process(contains("reservation-confirmed-it"), any(IContext.class));
    }

    @Test
    void smtpRuntimeFailurePropagatesAsMailSendException() {
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(MimeMessage.class));

        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, ROOM_GENERIC,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, "RES-004", LOCALE_IT,
                null, null, null);

        assertThrows(org.springframework.mail.MailSendException.class,
                () -> service.sendReservationConfirmed(req));
    }

    @Test
    void sendReservationConfirmedSubjectContainsHotelName() {
        final ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, ROOM_GENERIC,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4), 3, "RES-005", LOCALE_EN,
                null, null, null);

        service.sendReservationConfirmed(req);

        verify(mailSender).send(captor.capture());
        final MimeMessage sent = captor.getValue();
        try {
            assertTrue(sent.getSubject().contains(HOTEL_NAME),
                    "Subject should contain hotel name but was: " + sent.getSubject());
        } catch (final jakarta.mail.MessagingException e) {
            throw new AssertionError(SUBJECT_READ_ERROR, e);
        }
    }

    @Test
    void sendReservationConfirmedCustomSubjectOverridesDefault() {
        final ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, ROOM_GENERIC,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4), 3, "RES-006", LOCALE_IT,
                "Il tuo soggiorno ti aspetta!", null, null);

        service.sendReservationConfirmed(req);

        verify(mailSender).send(captor.capture());
        final MimeMessage sent = captor.getValue();
        try {
            org.junit.jupiter.api.Assertions.assertEquals("Il tuo soggiorno ti aspetta!", sent.getSubject());
        } catch (final jakarta.mail.MessagingException e) {
            throw new AssertionError(SUBJECT_READ_ERROR, e);
        }
    }

    @Test
    void sendReservationConfirmedFromHeaderUsesHotelName() {
        final ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        final String distinctHotelName = "Grand Hotel Roma";
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                GUEST_EMAIL, GUEST_NAME, distinctHotelName, ROOM_GENERIC,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4), 3, "RES-007", LOCALE_IT,
                null, null, null);

        service.sendReservationConfirmed(req);

        verify(mailSender).send(captor.capture());
        assertFromHeaderContains(captor.getValue(), distinctHotelName);
    }

    @Test
    void sendCheckinBlankHotelNameFallsBackToPlatformFromName() {
        final ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        final CheckinNotificationRequest req = new CheckinNotificationRequest(
                GUEST_EMAIL, GUEST_NAME, "   ", ROOM_NUMBER_101,
                LocalDate.of(2026, 8, 5), LOCALE_IT);

        service.sendCheckin(req);

        verify(mailSender).send(captor.capture());
        assertFromHeaderContains(captor.getValue(), FROM_NAME);
    }

    private static void assertFromHeaderContains(final MimeMessage message, final String expected) {
        try {
            assertTrue(message.getFrom()[0].toString().contains(expected),
                    "From header should contain \"" + expected + "\" but was: " + message.getFrom()[0]);
        } catch (final jakarta.mail.MessagingException e) {
            throw new AssertionError("Could not read From header", e);
        }
    }

    @Test
    void sendCheckoutBlankCustomSubjectFallsBackToDefault() {
        final ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        final CheckoutNotificationRequest req = new CheckoutNotificationRequest(
                GUEST_EMAIL, GUEST_NAME, HOTEL_NAME, ROOM_NUMBER_101,
                LocalDateTime.of(2026, 8, 1, 14, 0),
                LocalDateTime.of(2026, 8, 5, 11, 0),
                List.of(), BigDecimal.valueOf(400), CURRENCY_EUR, LOCALE_IT,
                "   ", null, null);

        service.sendCheckout(req);

        verify(mailSender).send(captor.capture());
        final MimeMessage sent = captor.getValue();
        try {
            assertTrue(sent.getSubject().startsWith("Riepilogo soggiorno e fattura"),
                    "Blank custom subject should fall back to default but was: " + sent.getSubject());
        } catch (final jakarta.mail.MessagingException e) {
            throw new AssertionError(SUBJECT_READ_ERROR, e);
        }
    }
}
