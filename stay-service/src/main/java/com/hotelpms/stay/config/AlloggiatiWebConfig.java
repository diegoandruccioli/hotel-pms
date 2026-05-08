package com.hotelpms.stay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    /**
     * Creates a {@link RestTemplate} for Alloggiati Web portal submissions.
     * The default constructor relies on the JVM truststore — no
     * {@code TrustAllCerts} manager or hostname-verifier bypass is applied.
     *
     * @return a TLS-validating {@link RestTemplate}
     */
    @Bean("alloggiatiRestTemplate")
    public RestTemplate alloggiatiRestTemplate() {
        return new RestTemplate();
    }
}
