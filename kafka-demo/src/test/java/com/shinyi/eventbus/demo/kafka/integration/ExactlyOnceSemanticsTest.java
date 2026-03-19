package com.shinyi.eventbus.demo.kafka.integration;

import com.shinyi.eventbus.demo.kafka.consumer.ManualOffsetConsumer;
import com.shinyi.eventbus.demo.kafka.producer.IdempotentKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end exactly-once semantics test.
 */
@Slf4j
public class ExactlyOnceSemanticsTest extends BaseIntegrationTest {

    @Test
    void testExactlyOnceEndToEnd() throws Exception {
        String topic = "eos-test-" + System.currentTimeMillis();
        String groupId = "eos-consumer-group-" + System.currentTimeMillis();
        int messageCount = 1000;

        // Step 1: Produce messages with idempotent producer
        log.info("Step 1: Producing {} messages with idempotent producer", messageCount);

        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("acks", "all");
        producerProps.put("retries", Integer.MAX_VALUE);
        producerProps.put("enable.idempotence", true);

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, producerProps)) {

            IdempotentKafkaProducer.PerformanceResult produceResult =
                producer.sendBatch(messageCount, 1024, 500, 10);

            assertEquals(messageCount, produceResult.successCount,
                "All messages should be produced successfully");
            log.info("Produced {} messages in {} ms",
                produceResult.successCount, produceResult.durationMs);
        }

        // Wait for messages to be available
        Thread.sleep(2000);

        // Step 2: Consume messages with manual offset commit
        log.info("Step 2: Consuming {} messages with manual offset commit", messageCount);

        Properties consumerProps = new Properties();
        consumerProps.put("enable.auto.commit", "false");
        consumerProps.put("max.poll.records", "500");

        AtomicInteger processedCount = new AtomicInteger(0);
        ManualOffsetConsumer.MessageProcessor processor = (key, value) -> {
            processedCount.incrementAndGet();
            return value != null && value.length > 0;
        };

        try (ManualOffsetConsumer consumer =
             new ManualOffsetConsumer(bootstrapServers, topic, groupId, consumerProps, 1000)) {

            ManualOffsetConsumer.ConsumptionResult consumeResult =
                consumer.consumeWithManualCommit(messageCount, 60, processor);

            assertEquals(messageCount, consumeResult.successCount,
                "All messages should be consumed successfully");
            log.info("Consumed {} messages in {} ms",
                consumeResult.successCount, consumeResult.durationMs);
        }

        // Verify exactly-once
        assertEquals(messageCount, processedCount.get(),
            "Should have processed exactly " + messageCount + " messages");
    }

    @Test
    void testManualCommitPreventsDuplicates() throws Exception {
        String topic = "manual-commit-test-" + System.currentTimeMillis();
        String groupId = "manual-commit-group-" + System.currentTimeMillis();
        int messageCount = 500;

        // Produce messages
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("acks", "all");

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, producerProps)) {
            producer.sendBatch(messageCount, 512, 250, 5);
        }

        Thread.sleep(1000);

        // First consumption
        Properties consumerProps = new Properties();
        consumerProps.put("enable.auto.commit", "false");
        consumerProps.put("max.poll.records", "100");

        AtomicInteger firstPassCount = new AtomicInteger(0);

        try (ManualOffsetConsumer consumer =
             new ManualOffsetConsumer(bootstrapServers, topic, groupId, consumerProps, 1000)) {

            consumer.consumeWithManualCommit(messageCount, 30,
                (key, value) -> {
                    firstPassCount.incrementAndGet();
                    return true;
                });
        }

        assertEquals(messageCount, firstPassCount.get(),
            "First pass should consume all messages");

        // Second consumption with same group should get NO new messages
        AtomicInteger secondPassCount = new AtomicInteger(0);

        try (ManualOffsetConsumer consumer =
             new ManualOffsetConsumer(bootstrapServers, topic, groupId, consumerProps, 1000)) {

            consumer.consumeWithManualCommit(messageCount, 30,
                (key, value) -> {
                    secondPassCount.incrementAndGet();
                    return true;
                });
        }

        // Should get 0 new messages since offsets were committed
        assertEquals(0, secondPassCount.get(),
            "Second pass should get 0 messages since offsets were committed");
    }
}
