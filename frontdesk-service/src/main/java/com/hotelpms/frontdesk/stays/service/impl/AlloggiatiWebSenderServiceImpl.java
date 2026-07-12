package com.hotelpms.frontdesk.stays.service.impl;

import com.hotelpms.frontdesk.exception.ExternalServiceException;
import com.hotelpms.frontdesk.stays.domain.HotelSettings;
import com.hotelpms.frontdesk.stays.repository.HotelSettingsRepository;
import com.hotelpms.frontdesk.stays.security.AlloggiatiCredentialEncryptor;
import com.hotelpms.frontdesk.stays.service.AlloggiatiReportService;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Submits the Alloggiati Web report to the Polizia di Stato SOAP service.
 *
 * <h3>Protocol (discovered from WSDL at /service/Service.asmx?WSDL)</h3>
 * <ol>
 *   <li>POST {@code GenerateToken(Utente, Password, WsKey)} → receives a session token.</li>
 *   <li>POST {@code Send(Utente, token, ElencoSchedine)} with the token and
 *       the array of 168-char records → receives {@code EsitoOperazioneServizio}.</li>
 * </ol>
 * When {@code alloggiati.web.dry-run=true} the service calls {@code Test} instead of
 * {@code Send} — the portal validates the data but does not record it permanently.
 * Use {@code dry-run=true} in all non-production environments.
 *
 * <h3>Security (T-STAY-03)</h3>
 * The injected {@link RestOperations} uses the JVM default SSL context; no custom
 * TrustManager is installed, ensuring full TLS chain validation on every request.
 * Credentials are never hardcoded: each hotel may configure its own PS portal
 * username/password/WsKey (encrypted at rest, see {@link AlloggiatiCredentialEncryptor}),
 * resolved fresh on every {@link #submitReport}; a hotel that has not configured
 * its own credentials falls back to the global {@code alloggiati.web.*} environment
 * variables (single-hotel pilot behavior, unchanged).
 *
 * <h3>SOAP namespace note</h3>
 * The service namespace is configurable via {@code alloggiati.web.ws-namespace}.
 * The default ({@value #DEFAULT_WS_NAMESPACE}) was inferred from the WSDL
 * at {@code https://alloggiatiweb.poliziadistato.it/service/Service.asmx?WSDL}.
 * If the portal rejects requests with a namespace fault, update this property.
 */
@Service
@Slf4j
public class AlloggiatiWebSenderServiceImpl implements AlloggiatiWebSenderService {

    /** Default SOAP namespace inferred from the WSDL targetNamespace. */
    public static final String DEFAULT_WS_NAMESPACE =
            "http://alloggiatiweb.poliziadistato.it/PortaleAlloggiati/Service/";

    private static final String LOG_PREFIX = "[STAY]";
    private static final String CRLF = "\r\n";
    private static final String DQUOTE = "\"";
    private static final String DQUOTE_GT = "\">";
    private static final String SOAP_ENVELOPE_OPEN =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soapenv:Body>";
    private static final String SOAP_ENVELOPE_CLOSE = "</soapenv:Body></soapenv:Envelope>";
    private static final String SOAP_ACTION_GENERATE_TOKEN = "AlloggiatiService/GenerateToken";
    private static final String SOAP_ACTION_SEND = "AlloggiatiService/Send";
    private static final String SOAP_ACTION_TEST = "AlloggiatiService/Test";
    private static final String MEDIA_TYPE_XML = "text/xml;charset=UTF-8";
    private static final String ARRAY_NS =
            "http://schemas.microsoft.com/2003/10/Serialization/Arrays";
    private static final int SEND_BODY_INITIAL_CAPACITY = 4096;

    private final AlloggiatiReportService alloggiatiReportService;
    private final RestOperations restOperations;
    private final HotelSettingsRepository hotelSettingsRepository;
    private final AlloggiatiCredentialEncryptor credentialEncryptor;
    private final String serviceUrl;
    private final String defaultUsername;
    private final String defaultPassword;
    private final String defaultWsKey;
    private final String wsNamespace;
    private final boolean dryRun;

    /**
     * Constructs the sender with all required dependencies and configuration.
     *
     * @param alloggiatiReportService generates the 168-char tracciato
     * @param restOperations          TLS-validating HTTP client (JVM truststore)
     * @param hotelSettingsRepository resolves per-hotel credential overrides
     * @param credentialEncryptor     decrypts per-hotel password/WsKey at submission time
     * @param serviceUrl              SOAP endpoint URL ({@code alloggiati.web.service-url})
     * @param defaultUsername         fallback PS portal username ({@code alloggiati.web.username})
     * @param defaultPassword         fallback PS portal password ({@code alloggiati.web.password})
     * @param defaultWsKey            fallback Web Service Key ({@code alloggiati.web.ws-key})
     * @param wsNamespace             SOAP target namespace ({@code alloggiati.web.ws-namespace})
     * @param dryRun                  when {@code true} calls Test instead of Send
     */
    public AlloggiatiWebSenderServiceImpl(
            final AlloggiatiReportService alloggiatiReportService,
            @Qualifier("alloggiatiRestTemplate") final RestOperations restOperations,
            final HotelSettingsRepository hotelSettingsRepository,
            final AlloggiatiCredentialEncryptor credentialEncryptor,
            @Value("${alloggiati.web.service-url}") final String serviceUrl,
            @Value("${alloggiati.web.username}") final String defaultUsername,
            @Value("${alloggiati.web.password}") final String defaultPassword,
            @Value("${alloggiati.web.ws-key}") final String defaultWsKey,
            @Value("${alloggiati.web.ws-namespace:" + DEFAULT_WS_NAMESPACE + "}") final String wsNamespace,
            @Value("${alloggiati.web.dry-run:false}") final boolean dryRun) {
        this.alloggiatiReportService = alloggiatiReportService;
        this.restOperations = restOperations;
        this.hotelSettingsRepository = hotelSettingsRepository;
        this.credentialEncryptor = credentialEncryptor;
        this.serviceUrl = serviceUrl;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
        this.defaultWsKey = defaultWsKey;
        this.wsNamespace = wsNamespace;
        this.dryRun = dryRun;
    }

    /**
     * Resolves the Alloggiati Web credentials to use for the given hotel: its own,
     * if fully configured, otherwise the global fallback.
     *
     * @param hotelId the hotel UUID
     * @return the credentials to use for this submission
     */
    private AlloggiatiCredentials resolveCredentials(@NonNull final UUID hotelId) {
        return hotelSettingsRepository.findById(hotelId)
                .filter((@NonNull HotelSettings hs) -> hs.hasAlloggiatiCredentials())
                .map(settings -> new AlloggiatiCredentials(
                        settings.getAlloggiatiUsername(),
                        credentialEncryptor.decrypt(settings.getAlloggiatiPasswordEncrypted()),
                        credentialEncryptor.decrypt(settings.getAlloggiatiWsKeyEncrypted())))
                .orElseGet(() -> new AlloggiatiCredentials(defaultUsername, defaultPassword, defaultWsKey));
    }

    /** {@inheritDoc} */
    @Override
    public void submitReport(final LocalDate date, final UUID hotelId) {
        log.info("{} ALLOGGIATI_SUBMISSION_START | date={} | hotelId={} | dryRun={}",
                LOG_PREFIX, date, hotelId, dryRun);

        final String report = alloggiatiReportService.generateReport(date, hotelId);
        if (report.isBlank()) {
            log.info("{} ALLOGGIATI_SUBMISSION_SKIPPED | date={} | reason=EMPTY_REPORT",
                    LOG_PREFIX, date);
            return;
        }

        final List<String> records = splitRecords(report);
        log.info("{} ALLOGGIATI_SUBMISSION_RECORDS | date={} | count={}", LOG_PREFIX, date, records.size());

        final AlloggiatiCredentials credentials = resolveCredentials(Objects.requireNonNull(hotelId));
        final String token = generateToken(credentials);
        sendSchedule(token, records, date, credentials);
    }

    // -----------------------------------------------------------------------
    // SOAP step 1: GenerateToken
    // -----------------------------------------------------------------------

    /**
     * Calls {@code GenerateToken} on the SOAP service and returns the session token.
     *
     * @param credentials the resolved Alloggiati Web credentials for this submission
     * @return the authentication token string
     * @throws ExternalServiceException if the token cannot be obtained
     */
    private String generateToken(final AlloggiatiCredentials credentials) {
        final String body = buildGenerateTokenBody(credentials);
        final String response = callSoap(body, SOAP_ACTION_GENERATE_TOKEN);
        final String token = extractXmlText(response, "token");
        if (token == null || token.isBlank()) {
            throw new ExternalServiceException("GenerateToken returned empty token — check credentials and WsKey");
        }
        log.debug("{} ALLOGGIATI_TOKEN_OBTAINED", LOG_PREFIX);
        return token;
    }

    /**
     * Builds the SOAP envelope for {@code GenerateToken}.
     *
     * @param credentials the resolved Alloggiati Web credentials for this submission
     * @return SOAP request body as a string
     */
    private String buildGenerateTokenBody(final AlloggiatiCredentials credentials) {
        return SOAP_ENVELOPE_OPEN
                + "<GenerateToken xmlns=" + DQUOTE + wsNamespace + DQUOTE_GT
                + "<Utente>" + xmlEscape(credentials.username()) + "</Utente>"
                + "<Password>" + xmlEscape(credentials.password()) + "</Password>"
                + "<WsKey>" + xmlEscape(credentials.wsKey()) + "</WsKey>"
                + "</GenerateToken>"
                + SOAP_ENVELOPE_CLOSE;
    }

    // -----------------------------------------------------------------------
    // SOAP step 2: Send / Test
    // -----------------------------------------------------------------------

    /**
     * Calls {@code Send} (or {@code Test} in dry-run mode) with the guest records.
     *
     * @param token       session token from {@link #generateToken}
     * @param records     individual 168-char guest records
     * @param date        the check-in date (for logging)
     * @param credentials the resolved Alloggiati Web credentials for this submission
     * @throws ExternalServiceException if the portal returns an error
     */
    private void sendSchedule(
            final String token, final List<String> records, final LocalDate date,
            final AlloggiatiCredentials credentials) {
        final String operation = dryRun ? "Test" : "Send";
        final String soapAction = dryRun ? SOAP_ACTION_TEST : SOAP_ACTION_SEND;
        final String body = buildSendBody(operation, token, records, credentials);

        final String response = callSoap(body, soapAction);
        final String esito = extractXmlText(response, "esito");

        if (!"true".equalsIgnoreCase(esito)) {
            final String errCode = extractXmlText(response, "ErroreCod");
            final String errDesc = extractXmlText(response, "ErroreDes");
            log.error("{} ALLOGGIATI_SUBMISSION_FAILED | date={} | operation={} | code={} | desc={}",
                    LOG_PREFIX, date, operation, errCode, errDesc);
            throw new ExternalServiceException(
                    "Alloggiati Web " + operation + " failed: [" + errCode + "] " + errDesc);
        }

        log.info("{} ALLOGGIATI_SUBMISSION_SUCCESS | date={} | operation={} | rows={}",
                LOG_PREFIX, date, operation, records.size());
    }

    /**
     * Builds the SOAP envelope for {@code Send} or {@code Test}.
     *
     * @param operation   "Send" or "Test"
     * @param token       the authentication token
     * @param records     the 168-char guest records
     * @param credentials the resolved Alloggiati Web credentials for this submission
     * @return SOAP request body as a string
     */
    private String buildSendBody(
            final String operation, final String token, final List<String> records,
            final AlloggiatiCredentials credentials) {
        final StringBuilder sb = new StringBuilder(SEND_BODY_INITIAL_CAPACITY);
        sb.append(SOAP_ENVELOPE_OPEN)
          .append('<').append(operation).append(" xmlns=").append(DQUOTE).append(wsNamespace).append(DQUOTE_GT)
          .append("<Utente>").append(xmlEscape(credentials.username())).append("</Utente><token>")
          .append(xmlEscape(token)).append("</token><ElencoSchedine>");
        for (final String record : records) {
            sb.append("<string xmlns=").append(DQUOTE).append(ARRAY_NS).append(DQUOTE_GT)
              .append(xmlEscape(record)).append("</string>");
        }
        sb.append("</ElencoSchedine></").append(operation).append('>')
          .append(SOAP_ENVELOPE_CLOSE);
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // HTTP / SOAP transport
    // -----------------------------------------------------------------------

    /**
     * Posts a SOAP envelope to the service URL and returns the raw response body.
     *
     * @param soapBody   the SOAP envelope XML string
     * @param soapAction the SOAPAction header value
     * @return raw XML response body
     * @throws ExternalServiceException on HTTP or connection error
     */
    private String callSoap(final String soapBody, final String soapAction) {
        final MultiValueMap<String, String> headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MEDIA_TYPE_XML);
        headers.set("SOAPAction", DQUOTE + soapAction + DQUOTE);

        final HttpEntity<String> entity = new HttpEntity<>(soapBody, headers);
        try {
            final ResponseEntity<String> resp =
                    restOperations.postForEntity(Objects.requireNonNull(serviceUrl), entity, String.class);
            return resp.getBody() != null ? resp.getBody() : "";
        } catch (final RestClientException ex) {
            log.error("{} ALLOGGIATI_SOAP_ERROR | action={} | reason={}", LOG_PREFIX, soapAction, ex.getMessage());
            throw new ExternalServiceException("Alloggiati Web SOAP call failed: " + ex.getMessage(), ex);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Splits a multi-record report string into a list of individual 168-char records.
     * Records in the report are separated by CR+LF (the last record has no trailing CRLF).
     *
     * @param report the full report string
     * @return ordered list of individual record strings
     */
    private static List<String> splitRecords(final String report) {
        if (report == null || report.isBlank()) {
            return List.of();
        }
        return List.of(report.split(CRLF, -1));
    }

    /**
     * Extracts the text content of the first occurrence of an XML element by local name.
     * Uses the JDK DOM parser; does not require additional XML libraries.
     *
     * @param xml       the raw XML/SOAP response
     * @param localName the element local name (no namespace prefix)
     * @return the trimmed text content, or {@code null} if not found or on parse error
     */
    private static String extractXmlText(final String xml, final String localName) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            final var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            final var doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            final var nodes = doc.getElementsByTagNameNS("*", localName);
            if (nodes.getLength() == 0) {
                return null;
            }
            return nodes.item(0).getTextContent().trim();
        } catch (final ParserConfigurationException | SAXException | IOException ex) {
            log.warn("{} XML_PARSE_ERROR | element={} | detail={}", LOG_PREFIX, localName, ex.getMessage());
            return null;
        }
    }

    /**
     * Escapes the five XML special characters in a string value.
     *
     * @param value raw string (may be null)
     * @return XML-safe string; empty string if value is null
     */
    private static String xmlEscape(final String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Resolved Alloggiati Web credentials for a single {@link #submitReport} call —
     * either the hotel's own (decrypted) or the global fallback.
     *
     * @param username the PS portal username to use for this submission
     * @param password the PS portal password to use for this submission
     * @param wsKey    the Web Service Key to use for this submission
     */
    private record AlloggiatiCredentials(String username, String password, String wsKey) {
    }
}
