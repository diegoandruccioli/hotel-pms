package com.hotelpms.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Main application class for the Spring Cloud Config Server.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServiceApplication {

    /**
     * Dummy instance method to prevent PMD and Checkstyle from treating this as a utility class.
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
        SpringApplication.run(ConfigServiceApplication.class, args);
    }

}
