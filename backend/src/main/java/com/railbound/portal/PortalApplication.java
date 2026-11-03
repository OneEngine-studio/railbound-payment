package com.railbound.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Railbound portal backend.
 *
 * <p>{@code @EnableScheduling} powers the transactional outbox relay (player
 * notifications) and the payment reconciliation job.</p>
 */
@SpringBootApplication
@EnableScheduling
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
