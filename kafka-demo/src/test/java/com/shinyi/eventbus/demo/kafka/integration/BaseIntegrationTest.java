package com.shinyi.eventbus.demo.kafka.integration;

import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for integration tests using external Kafka.
 * Assumes Kafka is running on localhost:9092.
 */
public abstract class BaseIntegrationTest {

    protected static String bootstrapServers = "localhost:9092";

    @BeforeAll
    static void setup() {
        // Use external Kafka - no container management needed
    }
}
