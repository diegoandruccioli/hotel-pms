package com.hotelpms.frontdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main class for the Frontdesk Service application.
 *
 * <p>Consolidates the former {@code inventory-service}, {@code reservation-service} and
 * {@code stay-service} into a single deployable covering the room lifecycle bounded
 * context (rooms, reservations, stays, hotel settings, Alloggiati Web). See ADR-001 in
 * {@code backup/DECISIONS.md}.
 */
@SpringBootApplication
@EnableFeignClients
@EnableJpaAuditing
@EnableCaching
public class FrontdeskApplication {

    /**
     * Dummy instance method to prevent PMD and Checkstyle from treating this as a
     * utility class.
     */
    public void init() {
        // Not a utility class
    }

    /**
     * Main method.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(FrontdeskApplication.class, args);
    }
}
