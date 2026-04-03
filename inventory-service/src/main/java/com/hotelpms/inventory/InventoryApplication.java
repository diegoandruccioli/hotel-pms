package com.hotelpms.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main class for the Inventory Service application.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
public class InventoryApplication {

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
        SpringApplication.run(InventoryApplication.class, args);
    }
}
