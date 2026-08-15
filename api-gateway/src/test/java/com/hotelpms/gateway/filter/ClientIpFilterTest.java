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
        filter = new ClientIpFilter();
    }

    private ServerWebExchange run(@NonNull final MockServerHttpRequest request) {
        final MockServerWebExchange exchange = MockServerWebExchange.from(request);
        final AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        StepVerifier.create(
                filter.filter(exchange, ex -> {
                    downstream.set(ex);
                    ex.getResponse().setStatusCode(HttpStatus.OK);
                    return ex.getResponse().setComplete();
                })
        ).verifyComplete();
        return downstream.get();
    }

    @Nested
    @DisplayName("Header injection")
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
