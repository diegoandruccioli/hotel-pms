package com.hotelpms.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Notification Service.
 */
@SpringBootApplication
public class NotificationApplication {

    /**
     * Dummy instance method to prevent PMD/Checkstyle treating this as a utility class.
     */
    public void init() {
        // not a utility class
    }

    /**
     * Main method.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
