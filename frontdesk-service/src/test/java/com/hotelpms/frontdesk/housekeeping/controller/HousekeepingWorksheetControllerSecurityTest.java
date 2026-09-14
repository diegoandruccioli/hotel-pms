package com.hotelpms.frontdesk.housekeeping.controller;

import com.hotelpms.frontdesk.housekeeping.dto.HousekeepingWorksheetResponse;
import com.hotelpms.frontdesk.housekeeping.service.BusinessDateResolver;
import com.hotelpms.frontdesk.housekeeping.service.HousekeepingWorksheetService;
import com.hotelpms.frontdesk.security.SecurityConfig;
import com.hotelpms.frontdesk.stays.dto.HotelSettingsResponse;
import com.hotelpms.frontdesk.stays.service.HotelSettingsService;
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
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-slice test for {@link HousekeepingWorksheetController} — every
 * endpoint is open to ADMIN/OWNER/RECEPTIONIST (same set as {@code
 * RoomController}'s housekeeping-status endpoints — this is front-desk/
 * housekeeping work) but closed to every other role, e.g. GUEST. Same
 * pattern as {@code NightAuditControllerSecurityTest}: default Spring
 * Security auto-configuration excluded so only {@link SecurityConfig} + real
 * {@code @EnableMethodSecurity} AOP process the request.
 */
@SuppressWarnings({"null", "PMD.HardCodedCryptoKey"})
@WebMvcTest(
        controllers = HousekeepingWorksheetController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
class HousekeepingWorksheetControllerSecurityTest {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TEST_SECRET = "test-hmac-secret-minimum-32-characters-for-unit-tests";
    private static final String TEST_HOTEL_ID = "00000000-0000-0000-0000-000000000001";

    private static final String WORKSHEET_URL = "/api/v1/frontdesk/housekeeping/worksheet";
    private static final String WORKSHEET_PDF_URL = "/api/v1/frontdesk/housekeeping/worksheet.pdf";
    private static final String BUSINESS_DATE_URL = "/api/v1/frontdesk/housekeeping/business-date";
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
    private HousekeepingWorksheetService housekeepingWorksheetService;

    @MockitoBean
    private BusinessDateResolver businessDateResolver;

    @MockitoBean
    private HotelSettingsService hotelSettingsService;

    @MockitoBean
    private NonceStore nonceStore;

    @BeforeEach
    void stubNonceStoreAsAlwaysFresh() {
        when(nonceStore.claim(anyString(), anyLong())).thenReturn(true);
    }

    @Test
    void worksheetReturns403ForGuest() throws Exception {
        mockMvc.perform(withAuthHeaders(get(WORKSHEET_URL), USER_GUEST, ROLE_GUEST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void worksheetReturns200ForAdmin() throws Exception {
        when(housekeepingWorksheetService.getWorksheet(any(), any())).thenReturn(sampleWorksheet());

        mockMvc.perform(withAuthHeaders(get(WORKSHEET_URL), USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void worksheetReturns200ForReceptionist() throws Exception {
        when(housekeepingWorksheetService.getWorksheet(any(), any())).thenReturn(sampleWorksheet());

        mockMvc.perform(withAuthHeaders(get(WORKSHEET_URL), USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void worksheetPdfReturns403ForGuest() throws Exception {
        mockMvc.perform(withAuthHeaders(get(WORKSHEET_PDF_URL), USER_GUEST, ROLE_GUEST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void worksheetPdfReturns200ForReceptionist() throws Exception {
        when(businessDateResolver.resolve(any())).thenReturn(BUSINESS_DATE);
        when(housekeepingWorksheetService.getWorksheetPdf(any(), any())).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(withAuthHeaders(get(WORKSHEET_PDF_URL), USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void businessDateReturns403ForGuest() throws Exception {
        mockMvc.perform(withAuthHeaders(get(BUSINESS_DATE_URL), USER_GUEST, ROLE_GUEST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessDateReturns200ForReceptionist() throws Exception {
        when(businessDateResolver.resolve(any())).thenReturn(BUSINESS_DATE);
        when(hotelSettingsService.getOrCreate(any())).thenReturn(sampleSettings());

        mockMvc.perform(withAuthHeaders(get(BUSINESS_DATE_URL), USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    private static HousekeepingWorksheetResponse sampleWorksheet() {
        return new HousekeepingWorksheetResponse(
                BUSINESS_DATE, LocalDateTime.now(), true, "Test Hotel", List.of(), Map.of());
    }

    private static HotelSettingsResponse sampleSettings() {
        return new HotelSettingsResponse(
                UUID.fromString(TEST_HOTEL_ID), false, "Test Hotel", null, null, null, null, null, false,
                true, true, null, null, null, null, null, null, null, "Europe/Rome", 4);
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
