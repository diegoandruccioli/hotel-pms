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
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class AlloggiatiWebSenderServiceImplTest {

    private static final String PORTAL_URL = "https://alloggiatiweb.test/submit";
    private static final String USERNAME = "psuser";
    private static final String PASSWORD = "pspassword";
    private static final int YEAR_2026 = 2026;
    private static final int MONTH_APR = 4;
    private static final int DAY_10 = 10;
    private static final String SAMPLE_ROW =
            "Rossi|Mario|20/05/1985|PASSPORT|AA123456|10/04/2026\r\n";

    @Mock
    private AlloggiatiReportService alloggiatiReportService;

    private MockRestServiceServer server;
    private AlloggiatiWebSenderServiceImpl senderService;
    private LocalDate reportDate;

    @BeforeEach
    void setUp() {
        final RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        senderService = new AlloggiatiWebSenderServiceImpl(
                alloggiatiReportService, restTemplate, PORTAL_URL, USERNAME, PASSWORD);
        reportDate = LocalDate.of(YEAR_2026, MONTH_APR, DAY_10);
    }

    @Test
    void shouldPostReportToPortalUrl() {
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_ROW);
        server.expect(requestTo(PORTAL_URL))
                .andExpect(method(java.util.Objects.requireNonNull(HttpMethod.POST)))
                .andRespond(withSuccess());

        senderService.submitReport(reportDate);

        server.verify();
        verify(alloggiatiReportService).generateReport(reportDate);
    }

    @Test
    void shouldIncludeBasicAuthHeader() {
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_ROW);
        server.expect(requestTo(PORTAL_URL))
                .andExpect(method(java.util.Objects.requireNonNull(HttpMethod.POST)))
                .andExpect(header(HttpHeaders.AUTHORIZATION, startsWith("Basic ")))
                .andRespond(withSuccess());

        senderService.submitReport(reportDate);

        server.verify();
    }

    @Test
    void shouldSendTextPlainContentType() {
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_ROW);
        server.expect(requestTo(PORTAL_URL))
                .andExpect(method(java.util.Objects.requireNonNull(HttpMethod.POST)))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString("text/plain")))
                .andRespond(withSuccess());

        senderService.submitReport(reportDate);

        server.verify();
    }

    @Test
    void shouldThrowExternalServiceExceptionOnHttpServerError() {
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn(SAMPLE_ROW);
        server.expect(requestTo(PORTAL_URL))
                .andExpect(method(java.util.Objects.requireNonNull(HttpMethod.POST)))
                .andRespond(withServerError());

        assertThrows(ExternalServiceException.class, () -> senderService.submitReport(reportDate));
        server.verify();
    }

    @Test
    void shouldHandleEmptyReportWithoutError() {
        when(alloggiatiReportService.generateReport(reportDate)).thenReturn("");
        server.expect(requestTo(PORTAL_URL))
                .andExpect(method(java.util.Objects.requireNonNull(HttpMethod.POST)))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> senderService.submitReport(reportDate));
        server.verify();
    }
}
