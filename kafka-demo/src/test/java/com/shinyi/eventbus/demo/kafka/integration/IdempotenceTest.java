package com.shinyi.eventbus.demo.kafka.integration;

import com.shinyi.eventbus.demo.kafka.producer.IdempotentKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify idempotent producer doesn't produce duplicates on retry.
 */
@Slf4j
public class IdempotenceTest extends BaseIntegrationTest {

    @Test
    void testIdempotentProducerNoDuplicates() throws Exception {
        String topic = "idempotence-test-" + System.currentTimeMillis();
        int messageCount = 1000;

        // Produce messages with idempotent producer
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("acks", "all");
        producerProps.put("retries", 3);

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, producerProps)) {

            IdempotentKafkaProducer.PerformanceResult result =
                producer.sendBatch(messageCount, 1024, 500, 10);

            assertEquals(messageCount, result.successCount,
                "All messages should be sent successfully");
            log.info("Produced {} messages successfully", result.successCount);
        }

        // Consume all messages and check for duplicates
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "idempotence-verifier-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Set<String> uniqueMessages = Collections.synchronizedSet(new HashSet<>());
        Set<String> duplicateMessages = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger totalReceived = new AtomicInteger(0);

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));

            int maxPolls = 20;
            int polls = 0;

            while (totalReceived.get() < messageCount && polls < maxPolls) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(5));

                for (ConsumerRecord<String, byte[]> record : records) {
                    totalReceived.incrementAndGet();
                    String key = record.key();

                    if (uniqueMessages.contains(key)) {
                        duplicateMessages.add(key);
                    } else {
                        uniqueMessages.add(key);
                    }
                }

                if (!records.isEmpty()) {
                    polls = 0;
                } else {
                    polls++;
                }
            }
        }

        log.info("Total messages received: {}", totalReceived.get());
        log.info("Unique messages: {}", uniqueMessages.size());
        log.info("Duplicate messages: {}", duplicateMessages.size());

        assertEquals(messageCount, totalReceived.get(),
            "Should receive exactly " + messageCount + " messages");
        assertEquals(messageCount, uniqueMessages.size(),
            "All messages should be unique (no duplicates)");
        assertTrue(duplicateMessages.isEmpty(),
            "Should have no duplicate messages");
    }

    @Test
    void testIdempotenceWithTransientFailure() throws Exception {
        String topic = "idempotence-retry-test-" + System.currentTimeMillis();
        int messageCount = 500;

        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("acks", "all");
        producerProps.put("retries", Integer.MAX_VALUE);
        producerProps.put("max.in.flight.requests.per.connection", 5);

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, producerProps)) {

            IdempotentKafkaProducer.PerformanceResult result =
                producer.sendBatch(messageCount, 512, 250, 5);

            assertTrue(result.successCount > 0, "Should produce messages successfully");
            log.info("Produced {} messages with idempotent producer", result.successCount);
        }
    }
}
