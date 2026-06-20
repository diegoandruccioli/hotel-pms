package com.hotelpms.frontdesk.stays.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Spring configuration for the Alloggiati Web HTTP client.
 *
 * <p><strong>Security note (T-STAY-03):</strong> the {@link RestTemplate} bean
 * returned here uses <em>no</em> custom {@link javax.net.ssl.SSLContext}.
 * The JVM default SSL context is therefore active, which:
 * <ul>
 *   <li>validates the server certificate chain against the JVM truststore
 *       ({@code cacerts});</li>
 *   <li>verifies that the server hostname matches the certificate CN/SAN;</li>
 *   <li>rejects self-signed, expired, or revoked certificates.</li>
 * </ul>
 * This prevents Man-In-The-Middle interception of PII during transmission
 * to the Polizia di Stato Alloggiati Web portal.
 */
@Configuration
public class AlloggiatiWebConfig {

    /** Max time to establish the TCP connection to the PS portal, in milliseconds. */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /** Max time to wait for the SOAP response once connected, in milliseconds. */
    private static final int READ_TIMEOUT_MS = 20_000;

    /**
     * Creates a {@link RestTemplate} for Alloggiati Web portal submissions, using a
     * plain {@link SimpleClientHttpRequestFactory} (JDK {@code HttpURLConnection})
     * rather than letting Spring Boot auto-detect a pooled client.
     *
     * <p>A plain {@code new RestTemplate()} has no timeout at all, so a slow or
     * unreachable PS portal hangs the calling thread indefinitely — reproduced live
     * during QA. The first fix attempt used {@code RestTemplateBuilder.connectTimeout()
     * /.readTimeout()}, which still hung: this service's classpath transitively
     * includes Apache HttpClient5 (pulled in elsewhere in the project), so Spring Boot
     * auto-detects it and builds a <em>pooled</em> connection factory — and the hang
     * was in leasing a connection from that pool (a separate timeout from connect/read
     * that {@code RestTemplateBuilder}'s two settings don't cover), not in the TCP/TLS
     * handshake itself (verified independently: a raw SOAP POST to the real portal via
     * wget from inside the same container completed in ~0.2s). This integration submits
     * at most a few times per day per hotel — no pooling benefit justifies that extra
     * failure mode, so {@code SimpleClientHttpRequestFactory} sidesteps it entirely.
     *
     * <p>Security note (T-STAY-03): no custom {@link javax.net.ssl.SSLContext} is set
     * here either, so the JVM default SSL context remains active — full certificate
     * chain and hostname validation against the JVM truststore, no MITM bypass.
     *
     * @return a TLS-validating, timeout-bounded {@link RestTemplate}
     */
    @Bean("alloggiatiRestTemplate")
    public RestTemplate alloggiatiRestTemplate() {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
