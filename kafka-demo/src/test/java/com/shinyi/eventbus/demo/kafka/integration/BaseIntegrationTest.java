package com.shinyi.eventbus.demo.kafka.integration;

import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for integration tests using external Kafka.
 * Assumes Kafka is running on 10.10.10.30:9092.
 */
public abstract class BaseIntegrationTest {

    protected static String bootstrapServers = "10.10.10.30:9092";

    @BeforeAll
    static void setup() {
        // Use external Kafka - no container management needed
    }
}
