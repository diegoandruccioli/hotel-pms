package com.hotelpms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying that the Config Server enforces HTTP Basic
 * authentication on all configuration endpoints (T-CFG-03).
 *
 * <p>The full Spring context starts on a random port. Tests validate that:
 * <ul>
 *   <li>an unauthenticated request is rejected with 401 Unauthorized</li>
 *   <li>a request with valid credentials is accepted with 200 OK</li>
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "CONFIG_SERVER_PASSWORD=test-config-password",
        "CONFIG_SERVER_USERNAME=configuser"
    }
)
class ConfigSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Unauthenticated request to config endpoint returns 401 Unauthorized")
    void unauthenticatedRequestReturnsUnauthorized() {
        final ResponseEntity<String> response = restTemplate
            .getForEntity("http://localhost:" + port + "/auth-service/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Authenticated request to config endpoint returns 200 OK")
    void authenticatedRequestReturnsOk() {
        final ResponseEntity<String> response = restTemplate
            .withBasicAuth("configuser", "test-config-password")
            .getForEntity("http://localhost:" + port + "/auth-service/default", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
