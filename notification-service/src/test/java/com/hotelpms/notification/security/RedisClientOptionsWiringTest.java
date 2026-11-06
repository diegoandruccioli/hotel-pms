package com.hotelpms.notification.security;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot builds {@code SocketOptions} with only the connect timeout and applies the
 * {@link LettuceClientOptionsBuilderCustomizer} beans afterwards, so a customizer that sets its own socket options
 * replaces Boot's. This pins that the service's customizer keeps the configured connect timeout while adding the
 * dead-peer detection, against the real {@code DataRedisAutoConfiguration}.
 */
class RedisClientOptionsWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withUserConfiguration(CustomizerConfiguration.class);

    @Test
    @DisplayName("The connection factory keeps Boot's 2 s connect timeout and gains keepalive and TCP user timeout")
    void connectionFactoryKeepsTheConnectTimeoutAndDetectsDeadPeers() {
        runner.withPropertyValues("spring.data.redis.connect-timeout=2s").run(context -> {
            final ClientOptions clientOptions = context.getBean(LettuceConnectionFactory.class)
                    .getClientConfiguration().getClientOptions().orElseThrow();
            final SocketOptions socketOptions = clientOptions.getSocketOptions();

            assertThat(socketOptions.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(socketOptions.getKeepAlive().isEnabled()).isTrue();
            assertThat(socketOptions.isEnableTcpUserTimeout()).isTrue();
        });
    }

    @Test
    @DisplayName("Without the Redis properties (a web slice test loads SecurityConfig without them) the customizer "
            + "still works and leaves Lettuce's default connect timeout")
    void worksWithoutTheRedisProperties() {
        final ClientOptions.Builder builder = ClientOptions.builder();

        SecurityConfig.redisSocketOptionsCustomizer(
                new StaticListableBeanFactory().getBeanProvider(DataRedisProperties.class)).customize(builder);

        final SocketOptions socketOptions = builder.build().getSocketOptions();
        assertThat(socketOptions.getConnectTimeout()).isEqualTo(SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION);
        assertThat(socketOptions.getKeepAlive().isEnabled()).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    static final class CustomizerConfiguration {

        private CustomizerConfiguration() {
        }

        @Bean
        static LettuceClientOptionsBuilderCustomizer redisSocketOptionsCustomizer(
                final ObjectProvider<DataRedisProperties> properties) {
            return SecurityConfig.redisSocketOptionsCustomizer(properties);
        }
    }
}
