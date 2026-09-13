package com.hotelpms.frontdesk.housekeeping.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the {@link Clock} bean {@code BusinessDateResolverImpl} depends on,
 * purely so its "now" can be pinned in a test — see that class's javadoc.
 */
@Configuration
public class ClockConfig {

    /**
     * The production clock — real UTC system time. {@code
     * BusinessDateResolverImpl} converts it to the hotel's own timezone
     * itself, so this deliberately doesn't need to be zone-aware.
     *
     * @return a UTC system clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
