package com.hotelpms.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.notification.dto.CheckoutNotificationRequest;
import com.hotelpms.notification.dto.InvoiceLineItemDto;
import com.hotelpms.notification.dto.ReservationConfirmedRequest;
import com.hotelpms.notification.security.NonceStore;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings({"null", "PMD.HardCodedCryptoKey"})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.config.import=optional:configserver:",
            "spring.cloud.config.enabled=false",
            "internal.hmac.secret=test-secret-integration-12345",
            "notification.from-address=noreply@test.com",
            "notification.from-name=Test Hotel",
            "spring.data.redis.host=localhost",
            "spring.mail.host=localhost",
            "spring.mail.port=3025",
            "spring.mail.properties.mail.smtp.auth=false",
            "spring.mail.properties.mail.smtp.starttls.enable=false"
        }
)
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest {

    private static final String HMAC_SECRET = "test-secret-integration-12345";
    private static final String BASE_URL = "/internal/notifications";
    private static final String GUEST_EMAIL = "guest@example.com";
    private static final int SMTP_PORT = 3025;
    private static final int MAIL_WAIT_MS = 3000;
    private static GreenMail greenMail;

    @MockitoBean
    private NonceStore nonceStore;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeAll
    static void startMailServer() {
        greenMail = new GreenMail(new ServerSetup(SMTP_PORT, "localhost", "smtp"));
        greenMail.start();
    }

    @AfterAll
    static void stopMailServer() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @BeforeEach
    void setUp() {
        greenMail.reset();
        when(nonceStore.claim(any(), anyLong())).thenReturn(true);
    }

    @Test
    void checkoutEndpointSendsEmailToGreenMail() throws Exception {
        final CheckoutNotificationRequest req = new CheckoutNotificationRequest(
                GUEST_EMAIL,
                "Jane Smith",
                "Test Hotel",
                "101",
                LocalDateTime.of(2026, 8, 1, 14, 0),
                LocalDateTime.of(2026, 8, 5, 11, 0),
                List.of(new InvoiceLineItemDto("Room nights", BigDecimal.valueOf(320))),
                BigDecimal.valueOf(320),
                "EUR",
                "en",
                null, null, null);

        mockMvc.perform(post(BASE_URL + "/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .headers(buildHmacHeaders()))
                .andExpect(status().isNoContent());

        greenMail.waitForIncomingEmail(MAIL_WAIT_MS, 1);
        final MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals(GUEST_EMAIL, messages[0].getAllRecipients()[0].toString());
        assertTrue(messages[0].getSubject().contains("invoice") || messages[0].getSubject().contains("Stay summary"));
    }

    @Test
    void reservationConfirmedEndpointSendsEmailToGreenMail() throws Exception {
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                "booker@example.com",
                "John Doe",
                "Test Hotel",
                "Deluxe Room",
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 14),
                4,
                "RES-XYZ",
                "it",
                null, null, null);

        mockMvc.perform(post(BASE_URL + "/reservation-confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .headers(buildHmacHeaders()))
                .andExpect(status().isNoContent());

        greenMail.waitForIncomingEmail(MAIL_WAIT_MS, 1);
        final MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("booker@example.com", messages[0].getAllRecipients()[0].toString());
    }

    @Test
    void endpointMissingHmacHeadersReturns401() throws Exception {
        final ReservationConfirmedRequest req = new ReservationConfirmedRequest(
                GUEST_EMAIL, "John Doe", "Hotel", "Room",
                LocalDate.now(), LocalDate.now().plusDays(2), 2, "RES-000", "it",
                null, null, null);

        mockMvc.perform(post(BASE_URL + "/reservation-confirmed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @SuppressWarnings("PMD.LooseCoupling")
    private org.springframework.http.HttpHeaders buildHmacHeaders() {
        final String username = "gateway";
        final String role = "SYSTEM";
        final String hotelId = UUID.randomUUID().toString();
        final String timestamp = String.valueOf(System.currentTimeMillis());
        final String nonce = UUID.randomUUID().toString();
        final String signature = computeHmac(username, role, hotelId, timestamp, nonce);

        final org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Auth-User", username);
        headers.set("X-Auth-Role", role);
        headers.set("X-Auth-Hotel", hotelId);
        headers.set("X-Auth-Timestamp", timestamp);
        headers.set("X-Auth-Nonce", nonce);
        headers.set("X-Internal-Signature", signature);
        return headers;
    }

    private static String computeHmac(final String username, final String role,
            final String hotelId, final String timestamp, final String nonce) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] digest = mac.doFinal(
                    (username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC_FAILED", e);
        }
    }
}
