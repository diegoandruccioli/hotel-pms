package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.exception.ExternalServiceException;
import com.hotelpms.stay.service.AlloggiatiReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link AlloggiatiWebSenderServiceImpl}.
 *
 * <p>The tests verify the two-step SOAP protocol:
 * (1) {@code GenerateToken} → (2) {@code Send}/{@code Test}.
 * {@link MockRestServiceServer} intercepts all HTTP calls to the portal URL,
 * so no real network connections are made.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AlloggiatiWebSenderServiceImplTest {

    private static final String SERVICE_URL = "https://alloggiatiweb.test/service/Service.asmx";
    private static final String USERNAME = "psuser";
    private static final String PASSWORD = "pspass";
    private static final String WS_KEY = "test-ws-key-12345";
    private static final String WS_NAMESPACE = AlloggiatiWebSenderServiceImpl.DEFAULT_WS_NAMESPACE;

    private static final int YEAR = 2026;
    private static final int MONTH = 4;
    private static final int DAY = 10;

    private static final int LEN_COGNOME = 50;
    private static final int LEN_NOME = 30;
    private static final int LEN_NUMERO_DOC = 20;
    private static final String SOAP_ACTION_HEADER = "SOAPAction";
    private static final String SOAP_ENV_PREFIX =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">";

    // A realistic 168-char tracciato record (used to verify record content in payload)
    private static final String SAMPLE_RECORD =
            "16" + "15/04/2026" + "03"
            + padRight("Rossi", LEN_COGNOME)
            + padRight("Mario", LEN_NOME)
            + "1"
            + "20/05/1985"
            + "058091000" + "RM" + "100000100"
            + "100000100"
            + "PASOR"
            + padRight("AA1234567", LEN_NUMERO_DOC)
            + "058091000";

    private static final String SOAP_ENV_CLOSE = "</soap:Body></soap:Envelope>";

    private static final String TOKEN_RESPONSE =
            SOAP_ENV_PREFIX
            + "<soap:Body><GenerateTokenResponse>"
            + "<GenerateTokenResult><token>test-session-token</token></GenerateTokenResult>"
            + "</GenerateTokenResponse>" + SOAP_ENV_CLOSE;

    private static final String SEND_SUCCESS_RESPONSE =
            SOAP_ENV_PREFIX
            + "<soap:Body><SendResponse>"
            + "<SendResult><esito>true</esito></SendResult>"
            + "</SendResponse>" + SOAP_ENV_CLOSE;

    private static final String SEND_FAILURE_RESPONSE =
            SOAP_ENV_PREFIX
            + "<soap:Body><SendResponse>"
            + "<SendResult><esito>false</esito>"
            + "<ErroreCod>E001</ErroreCod>"
            + "<ErroreDes>Formato non valido</ErroreDes>"
            + "</SendResult>"
            + "</SendResponse>" + SOAP_ENV_CLOSE;

    @Mock
    private AlloggiatiReportService alloggiatiReportService;

    private MockRestServiceServer server;
    private RestTemplate restTemplate;
    private LocalDate reportDate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        reportDate = LocalDate.of(YEAR, MONTH, DAY);
    }

    private AlloggiatiWebSenderServiceImpl buildService(final boolean dryRun) {
        return new AlloggiatiWebSenderServiceImpl(
                alloggiatiReportService,
                restTemplate,
                SERVICE_URL, USERNAME, PASSWORD, WS_KEY, WS_NAMESPACE, dryRun);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String padRight(final String s, final int len) {
        return String.format("%-" + len + "s", s);
    }

    // -----------------------------------------------------------------------
    // Two-step SOAP flow
    // -----------------------------------------------------------------------

    @Test
    void shouldCallGenerateTokenThenSend() {
        final AlloggiatiWebSenderServiceImpl service = buildService(false);
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_RECORD);

        server.expect(requestTo(SERVICE_URL))
                .andExpect(method(java.util.Objects.requireNonNull(HttpMethod.POST)))
                .andExpect(header(SOAP_ACTION_HEADER, containsString("GenerateToken")))
                .andExpect(content().string(containsString("<GenerateToken")))
                .andRespond(withSuccess(TOKEN_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        server.expect(requestTo(SERVICE_URL))
                .andExpect(method(java.util.Objects.requireNonNull(HttpMethod.POST)))
                .andExpect(header(SOAP_ACTION_HEADER, containsString("Send")))
                .andExpect(content().string(containsString("<Send")))
                .andRespond(withSuccess(SEND_SUCCESS_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        service.submitReport(reportDate);

        server.verify();
        verify(alloggiatiReportService).generateReport(reportDate);
    }

    @Test
    void shouldCallTestInsteadOfSendInDryRunMode() {
        final AlloggiatiWebSenderServiceImpl service = buildService(true);
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_RECORD);

        server.expect(requestTo(SERVICE_URL))
                .andExpect(content().string(containsString("<GenerateToken")))
                .andRespond(withSuccess(TOKEN_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        server.expect(requestTo(SERVICE_URL))
                .andExpect(header(SOAP_ACTION_HEADER, containsString("Test")))
                .andExpect(content().string(containsString("<Test")))
                .andRespond(withSuccess(
                        SEND_SUCCESS_RESPONSE.replace("Send", "Test"),
                        org.springframework.http.MediaType.TEXT_XML));

        service.submitReport(reportDate);

        server.verify();
    }

    @Test
    void shouldIncludeSoapContentTypeHeader() {
        final AlloggiatiWebSenderServiceImpl service = buildService(false);
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_RECORD);

        server.expect(requestTo(SERVICE_URL))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString("text/xml")))
                .andRespond(withSuccess(TOKEN_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        server.expect(requestTo(SERVICE_URL))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString("text/xml")))
                .andRespond(withSuccess(SEND_SUCCESS_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        service.submitReport(reportDate);
    }

    @Test
    void shouldIncludeWsKeyInGenerateTokenBody() {
        final AlloggiatiWebSenderServiceImpl service = buildService(false);
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_RECORD);

        server.expect(requestTo(SERVICE_URL))
                .andExpect(content().string(containsString("<WsKey>")))
                .andExpect(content().string(containsString(WS_KEY)))
                .andRespond(withSuccess(TOKEN_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        server.expect(requestTo(SERVICE_URL))
                .andRespond(withSuccess(SEND_SUCCESS_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        service.submitReport(reportDate);
        server.verify();
    }

    @Test
    void shouldThrowExternalServiceExceptionOnTokenGenerationFailure() {
        final AlloggiatiWebSenderServiceImpl service = buildService(false);
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_RECORD);

        server.expect(requestTo(SERVICE_URL))
                .andRespond(withServerError());

        assertThrows(ExternalServiceException.class, () -> service.submitReport(reportDate));
    }

    @Test
    void shouldThrowExternalServiceExceptionWhenSendReturnsEsitoFalse() {
        final AlloggiatiWebSenderServiceImpl service = buildService(false);
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_RECORD);

        server.expect(requestTo(SERVICE_URL))
                .andRespond(withSuccess(TOKEN_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        server.expect(requestTo(SERVICE_URL))
                .andRespond(withSuccess(SEND_FAILURE_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        assertThrows(ExternalServiceException.class, () -> service.submitReport(reportDate));
    }

    @Test
    void shouldSkipSubmissionForEmptyReport() {
        final AlloggiatiWebSenderServiceImpl service = buildService(false);
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn("");

        // No HTTP calls expected — server.verify() would fail if any calls were made
        service.submitReport(reportDate);

        server.verify();
    }

    @Test
    void shouldIncludeRecordInElencoSchedine() {
        final AlloggiatiWebSenderServiceImpl service = buildService(false);
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_RECORD);

        server.expect(requestTo(SERVICE_URL))
                .andRespond(withSuccess(TOKEN_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        server.expect(requestTo(SERVICE_URL))
                .andExpect(content().string(containsString("<ElencoSchedine>")))
                .andExpect(content().string(containsString("Rossi")))
                .andRespond(withSuccess(SEND_SUCCESS_RESPONSE, org.springframework.http.MediaType.TEXT_XML));

        service.submitReport(reportDate);
        server.verify();
    }
}
