package com.shinyi.eventbus.demo.kafka;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KafkaConsumerPerformanceTest
 */
public class KafkaConsumerPerformanceTestTest {

    @Test
    public void testBuilderDefaultValues() {
        KafkaConsumerPerformanceTest test = KafkaConsumerPerformanceTest.builder()
                .bootstrapServers("10.10.10.30:9092")
                .topic("test-topic")
                .groupId("test-group")
                .build();

        assertNotNull(test);
    }

    @Test
    public void testBuilderWithAllParameters() {
        KafkaConsumerPerformanceTest test = KafkaConsumerPerformanceTest.builder()
                .bootstrapServers("10.10.10.30:9092")
                .topic("my-topic")
                .groupId("my-group")
                .autoOffsetReset("latest")
                .enableAutoCommit(false)
                .sessionTimeoutMs(60000)
                .maxPollRecords(1000)
                .maxPollIntervalMs(600000)
                .build();

        assertNotNull(test);
    }

    @Test
    public void testBuilderWithSecuritySettings() {
        KafkaConsumerPerformanceTest test = KafkaConsumerPerformanceTest.builder()
                .bootstrapServers("10.10.10.30:9092")
                .topic("secure-topic")
                .groupId("secure-group")
                .securityProtocol("SASL_PLAINTEXT")
                .saslMechanism("SCRAM-SHA-256")
                .username("admin")
                .password("secret")
                .build();

        assertNotNull(test);
    }
}
