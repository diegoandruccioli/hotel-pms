package com.hotelpms.frontdesk.nightaudit.controller;

import com.hotelpms.frontdesk.nightaudit.domain.NightAuditStatus;
import com.hotelpms.frontdesk.nightaudit.dto.NightAuditRunResponse;
import com.hotelpms.frontdesk.nightaudit.service.NightAuditService;
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
import org.springframework.data.domain.PageImpl;
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
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-slice test for {@link NightAuditController} — both endpoints are
 * open to ADMIN/OWNER/RECEPTIONIST (the night audit is night-shift front-desk
 * work; see GAP-26 in THREAT_MODEL.md) but closed to every other role, e.g.
 * GUEST. Same pattern as {@code CityTaxRateControllerSecurityTest}: default
 * Spring Security auto-configuration excluded so only {@link SecurityConfig}
 * + real {@code @EnableMethodSecurity} AOP process the request.
 */
@SuppressWarnings({"null", "PMD.HardCodedCryptoKey"})
@WebMvcTest(
        controllers = NightAuditController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
class NightAuditControllerSecurityTest {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TEST_SECRET = "test-hmac-secret-minimum-32-characters-for-unit-tests";
    private static final String TEST_HOTEL_ID = "00000000-0000-0000-0000-000000000001";

    private static final String BASE_URL = "/api/v1/frontdesk/night-audit";
    private static final String DATE_PARAM = "2026-06-15";
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 6, 15);

    private static final String HDR_USER = "X-Auth-User";
    private static final String HDR_ROLE = "X-Auth-Role";
    private static final String HDR_HOTEL = "X-Auth-Hotel";
    private static final String HDR_SIG = "X-Internal-Signature";
    private static final String HDR_TIMESTAMP = "X-Auth-Timestamp";
    private static final String HDR_NONCE = "X-Auth-Nonce";

    private static final String USER_RECEPT = "recept";
    private static final String ROLE_RECEPTIONIST = "RECEPTIONIST";
    private static final String USER_ADMIN = "admin";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String USER_GUEST = "guest";
    private static final String ROLE_GUEST = "GUEST";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private NightAuditService nightAuditService;

    @MockitoBean
    private NonceStore nonceStore;

    @BeforeEach
    void stubNonceStoreAsAlwaysFresh() {
        when(nonceStore.claim(anyString(), anyLong())).thenReturn(true);
    }

    @Test
    void runReturns403ForGuest() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        post(BASE_URL + "?date=" + DATE_PARAM),
                        USER_GUEST, ROLE_GUEST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void runReturns201ForAdmin() throws Exception {
        when(nightAuditService.run(any(), anyString())).thenReturn(sampleRun());

        mockMvc.perform(withAuthHeaders(
                        post(BASE_URL + "?date=" + DATE_PARAM),
                        USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isCreated());
    }

    @Test
    void runReturns201ForReceptionist() throws Exception {
        when(nightAuditService.run(any(), anyString())).thenReturn(sampleRun());

        mockMvc.perform(withAuthHeaders(
                        post(BASE_URL + "?date=" + DATE_PARAM),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isCreated());
    }

    @Test
    void historyReturns403ForGuest() throws Exception {
        mockMvc.perform(withAuthHeaders(get(BASE_URL), USER_GUEST, ROLE_GUEST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void historyReturns200ForAdmin() throws Exception {
        when(nightAuditService.getHistory(any())).thenReturn(new PageImpl<>(List.of(sampleRun())));

        mockMvc.perform(withAuthHeaders(get(BASE_URL), USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void historyReturns200ForReceptionist() throws Exception {
        when(nightAuditService.getHistory(any())).thenReturn(new PageImpl<>(List.of(sampleRun())));

        mockMvc.perform(withAuthHeaders(get(BASE_URL), USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    private static NightAuditRunResponse sampleRun() {
        return new NightAuditRunResponse(UUID.randomUUID(), BUSINESS_DATE, NightAuditStatus.COMPLETED,
                LocalDateTime.now(), LocalDateTime.now(), USER_ADMIN, 0, 0, 0L, 0L, 0, 0, List.of(), false, null);
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
