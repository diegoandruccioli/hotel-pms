package com.hotelpms.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ClientIpFilter}.
 *
 * <p>No Spring context is loaded. The filter is exercised through a
 * {@link MockServerWebExchange} with a chain lambda that captures the mutated
 * exchange seen downstream, allowing assertions on the header the next filter
 * (or the routed service) would actually receive.
 */
class ClientIpFilterTest {

    private ClientIpFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ClientIpFilter(false);
    }

    private ServerWebExchange run(final ClientIpFilter filterUnderTest, @NonNull final MockServerHttpRequest request) {
        final MockServerWebExchange exchange = MockServerWebExchange.from(request);
        final AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        StepVerifier.create(
                filterUnderTest.filter(exchange, ex -> {
                    downstream.set(ex);
                    ex.getResponse().setStatusCode(HttpStatus.OK);
                    return ex.getResponse().setComplete();
                })
        ).verifyComplete();
        return downstream.get();
    }

    private ServerWebExchange run(@NonNull final MockServerHttpRequest request) {
        return run(filter, request);
    }

    @Nested
    @DisplayName("Header injection (trust-forwarded-header=false, dev default)")
    class HeaderInjectionTests {

        @Test
        @DisplayName("injects X-Client-IP from the TCP remote address when header is absent")
        void injectsRemoteAddressWhenHeaderAbsent() {
            final ServerWebExchange downstream = run(
                    MockServerHttpRequest.post("/api/v1/auth/login")
                            .remoteAddress(new InetSocketAddress("203.0.113.9", 0))
                            .build());

            assertThat(downstream.getRequest().getHeaders().getFirst(ClientIpFilter.CLIENT_IP_HEADER))
                    .isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("overrides a client-supplied X-Client-IP with the real TCP remote address")
        void overridesClientSuppliedHeader() {
            final ServerWebExchange downstream = run(
                    MockServerHttpRequest.post("/api/v1/auth/login")
                            .remoteAddress(new InetSocketAddress("203.0.113.9", 0))
                            .header(ClientIpFilter.CLIENT_IP_HEADER, "1.2.3.4")
                            .build());

            assertThat(downstream.getRequest().getHeaders().get(ClientIpFilter.CLIENT_IP_HEADER))
                    .containsExactly("203.0.113.9");
        }

        @Test
        @DisplayName("GAP-17: ignores a client-supplied X-Real-IP too — direct :8080 access can't spoof it")
        void ignoresForwardedRealIpWhenTrustDisabled() {
            final ServerWebExchange downstream = run(
                    MockServerHttpRequest.post("/api/v1/auth/login")
                            .remoteAddress(new InetSocketAddress("203.0.113.9", 0))
                            .header("X-Real-IP", "1.2.3.4")
                            .build());

            assertThat(downstream.getRequest().getHeaders().getFirst(ClientIpFilter.CLIENT_IP_HEADER))
                    .isEqualTo("203.0.113.9");
        }
    }

    @Nested
    @DisplayName("GAP-17: header injection with trust-forwarded-header=true (prod, api-gateway:8080 not host-published)")
    class TrustForwardedHeaderTests {

        private final ClientIpFilter trustingFilter = new ClientIpFilter(true);

        @Test
        @DisplayName("uses nginx's X-Real-IP instead of the TCP peer (nginx's own container IP) when present")
        void usesXRealIpWhenTrusted() {
            final ServerWebExchange downstream = run(
                    trustingFilter,
                    MockServerHttpRequest.post("/api/v1/auth/login")
                            // TCP peer as seen by the gateway is nginx's container IP, not the real client.
                            .remoteAddress(new InetSocketAddress("172.20.0.5", 0))
                            .header("X-Real-IP", "203.0.113.9")
                            .build());

            assertThat(downstream.getRequest().getHeaders().getFirst(ClientIpFilter.CLIENT_IP_HEADER))
                    .isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("falls back to the TCP remote address when X-Real-IP is absent")
        void fallsBackToRemoteAddressWhenHeaderAbsent() {
            final ServerWebExchange downstream = run(
                    trustingFilter,
                    MockServerHttpRequest.post("/api/v1/auth/login")
                            .remoteAddress(new InetSocketAddress("203.0.113.9", 0))
                            .build());

            assertThat(downstream.getRequest().getHeaders().getFirst(ClientIpFilter.CLIENT_IP_HEADER))
                    .isEqualTo("203.0.113.9");
        }

        @Test
        @DisplayName("falls back to the TCP remote address when X-Real-IP is blank")
        void fallsBackToRemoteAddressWhenHeaderBlank() {
            final ServerWebExchange downstream = run(
                    trustingFilter,
                    MockServerHttpRequest.post("/api/v1/auth/login")
                            .remoteAddress(new InetSocketAddress("203.0.113.9", 0))
                            .header("X-Real-IP", "   ")
                            .build());

            assertThat(downstream.getRequest().getHeaders().getFirst(ClientIpFilter.CLIENT_IP_HEADER))
                    .isEqualTo("203.0.113.9");
        }
    }

    @Nested
    @DisplayName("Filter ordering")
    class FilterOrderTests {

        @Test
        @DisplayName("getOrder returns HIGHEST_PRECEDENCE + 3 (after CsrfFilter)")
        void shouldHaveCorrectOrder() {
            assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 3);
        }
    }
}
