package com.hotelpms.stay.service.impl;

import com.hotelpms.stay.exception.ExternalServiceException;
import com.hotelpms.stay.service.AlloggiatiReportService;
import com.hotelpms.stay.service.AlloggiatiWebSenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Submits the Alloggiati Web report to the Polizia di Stato portal.
 *
 * <p><strong>Security (T-STAY-03):</strong> the injected {@link RestOperations}
 * uses the JVM default SSL context — no custom {@link javax.net.ssl.TrustManager}
 * is installed, ensuring that the TLS certificate chain and hostname of the PS
 * portal are fully validated on every request. Credentials are read exclusively
 * from environment variables ({@code ALLOGGIATI_USERNAME},
 * {@code ALLOGGIATI_PASSWORD}) and are never hardcoded.
 */
@Service
@Slf4j
public class AlloggiatiWebSenderServiceImpl implements AlloggiatiWebSenderService {

    private static final String LOG_PREFIX = "[STAY]";

    private final AlloggiatiReportService alloggiatiReportService;
    private final RestOperations restOperations;
    private final String portalUrl;
    private final String username;
    private final String password;

    /**
     * Constructs the service with required dependencies and configuration.
     *
     * @param alloggiatiReportService generates the pipe-delimited report text
     * @param restOperations          TLS-validating HTTP client (JVM truststore),
     *                                injected via {@code alloggiatiRestTemplate} bean
     * @param portalUrl               Alloggiati Web endpoint from
     *                                {@code alloggiati.web.url}
     * @param username                PS portal username from
     *                                {@code alloggiati.web.username}
     * @param password                PS portal password from
     *                                {@code alloggiati.web.password}
     */
    public AlloggiatiWebSenderServiceImpl(
            final AlloggiatiReportService alloggiatiReportService,
            @Qualifier("alloggiatiRestTemplate") final RestOperations restOperations,
            @Value("${alloggiati.web.url}") final String portalUrl,
            @Value("${alloggiati.web.username}") final String username,
            @Value("${alloggiati.web.password}") final String password) {
        this.alloggiatiReportService = alloggiatiReportService;
        this.restOperations = restOperations;
        this.portalUrl = portalUrl;
        this.username = username;
        this.password = password;
    }

    /** {@inheritDoc} */
    @Override
    public void submitReport(final LocalDate date) {
        log.info("{} ALLOGGIATI_SUBMISSION_START | date={}", LOG_PREFIX, date);
        final String report = alloggiatiReportService.generateReport(date);
        final int rowCount = countRows(report);

        // Declare as MultiValueMap (interface) to satisfy PMD LooseCoupling
        final MultiValueMap<String, String> headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);
        headers.set(HttpHeaders.AUTHORIZATION, buildBasicAuthHeader());

        final HttpEntity<String> entity = new HttpEntity<>(report, headers);
        try {
            restOperations.postForEntity(java.util.Objects.requireNonNull(portalUrl), entity, Void.class);
            log.info("{} ALLOGGIATI_SUBMITTED | date={} | rows={}", LOG_PREFIX, date, rowCount);
        } catch (final RestClientException ex) {
            log.warn("{} ALLOGGIATI_SUBMISSION_FAILED | date={} | reason={}", LOG_PREFIX, date, ex.getMessage());
            throw new ExternalServiceException("Alloggiati Web submission failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Builds a Basic Authentication header value from the configured credentials.
     * The credentials are Base64-encoded as required by RFC 7617.
     *
     * @return the {@code Authorization: Basic ...} header value
     */
    private String buildBasicAuthHeader() {
        final String credentials = username + ":" + password;
        final String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Counts the number of {@code CRLF}-terminated rows in the report.
     *
     * @param report the generated report text
     * @return the number of complete rows
     */
    private static int countRows(final String report) {
        if (report == null || report.isBlank()) {
            return 0;
        }
        return (int) report.chars().filter(c -> c == '\n').count();
    }
}
