package com.hotelpms.billing.controller;

import com.hotelpms.billing.security.SecurityConfig;
import com.hotelpms.billing.service.FatturaPAService;
import com.hotelpms.billing.service.InvoiceService;
import com.hotelpms.billing.service.PdfInvoiceService;
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
import java.util.HexFormat;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration-level security tests for {@link InvoiceController}'s
 * fiscally-sensitive endpoints, protected by
 * {@code @PreAuthorize("hasAnyRole('ADMIN','OWNER')")} (round 2 exploratory
 * test, bug #5): {@code document-type}, {@code fatturaPA}, {@code sdi-status}
 * and the batch {@code export} were previously reachable by any authenticated
 * role, including RECEPTIONIST.
 *
 * <p>Modeled on {@code MenuItemControllerSecurityTest} (fb-service):
 * default Spring Security auto-configurations are excluded so only
 * {@link SecurityConfig} processes requests, exercising the real
 * {@code InternalAuthFilter} + {@code @EnableMethodSecurity} AOP path rather
 * than the standalone MockMvc setup used by {@link InvoiceControllerTest},
 * which never wires method security.
 */
@SuppressWarnings({"null", "PMD.HardCodedCryptoKey"})
@WebMvcTest(
        controllers = InvoiceController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
class InvoiceControllerSecurityTest {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TEST_SECRET = "test-hmac-secret-minimum-32-characters-for-unit-tests";
    private static final String TEST_HOTEL_ID = "00000000-0000-0000-0000-000000000001";

    private static final String BASE_URL = "/api/v1/invoices";
    private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

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

    private static final String DOCUMENT_TYPE_BODY = "{\"documentType\":\"RICEVUTA\"}";
    private static final String SDI_STATUS_BODY = "{\"sdiStatus\":\"SENT\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private InvoiceService invoiceService;

    @MockitoBean
    private PdfInvoiceService pdfInvoiceService;

    @MockitoBean
    private FatturaPAService fatturaPAService;

    @MockitoBean
    private NonceStore nonceStore;

    @BeforeEach
    void stubNonceStoreAsAlwaysFresh() {
        when(nonceStore.claim(anyString(), anyLong())).thenReturn(true);
    }

    @Test
    void updateDocumentTypeReturns403ForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        patch(BASE_URL + "/{id}/document-type", INVOICE_ID)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(DOCUMENT_TYPE_BODY),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void fatturaPAXmlReturns403ForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        get(BASE_URL + "/{id}/fatturaPA", INVOICE_ID),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateSdiStatusReturns403ForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        patch(BASE_URL + "/{id}/sdi-status", INVOICE_ID)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(SDI_STATUS_BODY),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportBatchReturns403ForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        get(BASE_URL + "/export?from=2026-01-01&to=2026-12-31"),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateDocumentTypeReturnsOkForAdmin() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        patch(BASE_URL + "/{id}/document-type", INVOICE_ID)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(DOCUMENT_TYPE_BODY),
                        USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void fatturaPAXmlReturnsOkForAdmin() throws Exception {
        when(fatturaPAService.generateXml(INVOICE_ID)).thenReturn(new byte[0]);

        mockMvc.perform(withAuthHeaders(
                        get(BASE_URL + "/{id}/fatturaPA", INVOICE_ID),
                        USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void updateSdiStatusReturnsOkForAdmin() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        patch(BASE_URL + "/{id}/sdi-status", INVOICE_ID)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(SDI_STATUS_BODY),
                        USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void exportBatchReturnsOkForAdmin() throws Exception {
        when(fatturaPAService.generateBatchZip(any(), any())).thenReturn(new byte[0]);

        mockMvc.perform(withAuthHeaders(
                        get(BASE_URL + "/export?from=2026-01-01&to=2026-12-31"),
                        USER_ADMIN, ROLE_ADMIN, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    @Test
    void invoiceByIdRemainsReachableForReceptionist() throws Exception {
        mockMvc.perform(withAuthHeaders(
                        get(BASE_URL + "/{id}", INVOICE_ID),
                        USER_RECEPT, ROLE_RECEPTIONIST, TEST_HOTEL_ID))
                .andExpect(status().isOk());
    }

    /**
     * Adds a fresh, validly-signed set of gateway headers (T-GW-08: a new
     * timestamp and nonce on every call) to the given request builder.
     *
     * @param builder  the request builder to add headers to
     * @param username the value for {@code X-Auth-User}
     * @param role     the value for {@code X-Auth-Role}
     * @param hotelId  the value for {@code X-Auth-Hotel}
     * @return the same builder, with all six gateway headers set
     */
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
