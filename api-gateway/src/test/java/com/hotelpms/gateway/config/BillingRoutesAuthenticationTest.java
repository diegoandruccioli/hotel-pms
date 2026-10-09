package com.hotelpms.gateway.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard for the gateway marker design: billing-service treats a request WITHOUT
 * {@code X-Gateway-Origin} as an internal service-to-service call (fail-open), and only
 * {@code AuthenticationFilter} sets that marker and signs the request. A route to billing that
 * skipped the filter would therefore either be rejected (no HMAC headers) or, worse, one day be
 * trusted by mistake. This test makes adding such a route a deliberate, reviewed decision.
 *
 * <p>Reads the real routes from {@code config-service}'s {@code api-gateway.yml} so it cannot
 * drift from what is deployed.
 */
class BillingRoutesAuthenticationTest {

    private static final Path GATEWAY_CONFIG =
            Path.of("..", "config-service", "src", "main", "resources", "config", "api-gateway.yml");

    private static final String BILLING_HOST = "billing-service";
    private static final String AUTHENTICATION_FILTER = "AuthenticationFilter";

    /**
     * Routes to billing that are allowed to skip {@code AuthenticationFilter}, with the reason.
     * Only the OpenAPI document: billing's own {@code InternalAuthFilter} answers 401 to it
     * (it exempts nothing but {@code /actuator}), so it exposes no data.
     */
    private static final Set<String> UNAUTHENTICATED_BILLING_ROUTES = Set.of("billing-service-docs");

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> routes() throws IOException {
        try (InputStream in = Files.newInputStream(GATEWAY_CONFIG)) {
            final Map<String, Object> root = new Yaml().load(in);
            Object node = root;
            for (final String key : new String[] {"spring", "cloud", "gateway", "server", "webflux", "routes"}) {
                node = ((Map<String, Object>) node).get(key);
            }
            return (List<Map<String, Object>>) node;
        }
    }

    private static boolean hasAuthenticationFilter(final Map<String, Object> route) {
        final Object filters = route.get("filters");
        if (!(filters instanceof List<?> list)) {
            return false;
        }
        return list.stream().anyMatch(f -> AUTHENTICATION_FILTER.equals(f instanceof Map<?, ?> m ? m.get("name") : f));
    }

    @Test
    void theGatewayConfigIsReadable() throws IOException {
        assertThat(routes()).isNotEmpty();
    }

    @Test
    void everyRouteToBillingRunsAuthenticationFilterUnlessExplicitlyAllowed() throws IOException {
        final List<String> offenders = routes().stream()
                .filter(route -> String.valueOf(route.get("uri")).contains(BILLING_HOST))
                .filter(route -> !hasAuthenticationFilter(route))
                .map(route -> String.valueOf(route.get("id")))
                .filter(id -> !UNAUTHENTICATED_BILLING_ROUTES.contains(id))
                .toList();

        assertThat(offenders)
                .as("routes to billing-service without AuthenticationFilter: it sets X-Gateway-Origin, "
                        + "and a request without the marker is trusted as internal")
                .isEmpty();
    }

    @Test
    void theMainBillingRouteExistsAndIsAuthenticated() throws IOException {
        final Map<String, Object> billing = routes().stream()
                .filter(route -> "billing-service".equals(route.get("id")))
                .findFirst()
                .orElseThrow();

        assertThat(hasAuthenticationFilter(billing)).isTrue();
    }
}
