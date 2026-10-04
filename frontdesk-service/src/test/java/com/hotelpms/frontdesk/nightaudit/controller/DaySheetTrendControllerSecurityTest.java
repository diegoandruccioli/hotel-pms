package com.hotelpms.frontdesk.nightaudit.controller;

import com.hotelpms.frontdesk.nightaudit.dto.DaySheetTrendResponse;
import com.hotelpms.frontdesk.nightaudit.service.DaySheetTrendService;
import com.hotelpms.frontdesk.security.SecurityConfig;
import com.hotelpms.internalauth.security.NonceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-slice test for {@link DaySheetTrendController} — open to
 * ADMIN/OWNER/RECEPTIONIST like the day-sheet it feeds, closed to every other
 * role. Same pattern as {@code NightAuditControllerSecurityTest}.
 */
@SuppressWarnings({"null", "PMD.HardCodedCryptoKey"})
@WebMvcTest(
        controllers = DaySheetTrendController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
class DaySheetTrendControllerSecurityTest {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TEST_SECRET = "test-hmac-secret-minimum-32-characters-for-unit-tests";
    private static final String TEST_HOTEL_ID = "00000000-0000-0000-0000-000000000001";

    private static final String URL = "/api/v1/frontdesk/day-sheet/trend?date=2026-10-04";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
    private static final int DEFAULT_DAYS = 7;

    private static final String HDR_USER = "X-Auth-User";
    private static final String HDR_ROLE = "X-Auth-Role";
    private static final String HDR_HOTEL = "X-Auth-Hotel";
    private static final String HDR_SIG = "X-Internal-Signature";
    private static final String HDR_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HDR_NONCE = "X-Auth-Nonce";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private DaySheetTrendService daySheetTrendService;

    @MockitoBean
    private NonceStore nonceStore;

    @BeforeEach
    void stubNonceStoreAsAlwaysFresh() {
        when(nonceStore.claim(anyString(), anyLong())).thenReturn(true);
    }

    private void stubService() {
        when(daySheetTrendService.getTrend(any(), anyInt(), any()))
                .thenReturn(new DaySheetTrendResponse(DATE.minusDays(DEFAULT_DAYS), DATE.minusDays(1), List.of()));
    }

    @Test
    void returns403ForGuestWithoutReachingTheService() throws Exception {
        mockMvc.perform(withAuthHeaders(get(URL), "guest", "GUEST", TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(daySheetTrendService);
    }

    @Test
    void returns200ForReceptionist() throws Exception {
        stubService();
        mockMvc.perform(withAuthHeaders(get(URL), "recept", "RECEPTIONIST", TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void returns200ForOwner() throws Exception {
        stubService();
        mockMvc.perform(withAuthHeaders(get(URL), "owner", "OWNER", TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void returns200ForAdmin() throws Exception {
        stubService();
        mockMvc.perform(withAuthHeaders(get(URL), "admin", "ADMIN", TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder withAuthHeaders(
            final MockHttpServletRequestBuilder builder,
            final String username, final String role, final String hotelId) {
        final String timestamp = String.valueOf(System.currentTimeMillis());
        final String nonce = UUID.randomUUID().toString();
        final String signature = hmac(username, role, hotelId, timestamp, nonce);
        return builder
                .header(HDR_USER, username)
                .header(HDR_ROLE, role)
                .header(HDR_HOTEL, hotelId)
                .header(HDR_TIMESTAMP, timestamp)
                .header(HDR_NONCE, nonce)
                .header(HDR_SIG, signature);
    }

    private static String hmac(final String username, final String role, final String hotelId,
            final String timestamp, final String nonce) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            final byte[] digest = mac.doFinal(
                    (username + ":" + role + ":" + hotelId + ":" + timestamp + ":" + nonce)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC_FAILED", e);
        }
    }
}
