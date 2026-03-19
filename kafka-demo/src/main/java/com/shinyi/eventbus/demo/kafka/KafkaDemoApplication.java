package com.shinyi.eventbus.demo.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Kafka Demo Application
 *
 * This application demonstrates Kafka producer and consumer performance testing
 * with support for various authentication mechanisms:
 * - PLAINTEXT (no authentication)
 * - SASL/PLAIN
 * - SASL/SCRAM-SHA-256, SASL/SCRAM-SHA-512
 * - Kerberos (GSSAPI)
 *
 * Configuration can be provided via:
 * 1. application.yml
 * 2. Environment variables (KAFKA_*)
 * 3. System properties (-Dkafka.*)
 */
@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
public class KafkaDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaDemoApplication.class, args);
    }
}
