package com.shinyi.eventbus.demo.kafka.integration;

import com.shinyi.eventbus.demo.kafka.producer.IdempotentKafkaProducer;
import com.shinyi.eventbus.demo.kafka.producer.ProducerPool;
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
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for multi-threaded producer safety.
 */
@Slf4j
public class MultiThreadedProducerTest extends BaseIntegrationTest {

    @Test
    void testProducerPoolThreadSafety() throws Exception {
        String topic = "mt-producer-test-" + System.currentTimeMillis();
        int poolSize = 4;
        int messagesPerProducer = 250;
        int totalMessages = poolSize * messagesPerProducer;

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("acks", "all");
        props.put("compression.type", "snappy");

        try (ProducerPool pool = new ProducerPool(bootstrapServers, topic, poolSize, props)) {
            IdempotentKafkaProducer.PerformanceResult result =
                pool.sendDistributed(totalMessages, 512);

            log.info("ProducerPool result: success={}, failed={}, throughput={}",
                result.successCount, result.failureCount, result.throughput);

            assertEquals(totalMessages, result.successCount + result.failureCount,
                "Should have attempted all messages");
            assertTrue(result.successCount > 0, "Should have some successful sends");
        }
    }

    @Test
    void testProducerPoolLoadBalancing() throws Exception {
        String topic = "mt-producer-balance-test-" + System.currentTimeMillis();
        int poolSize = 4;
        int totalMessages = 1000;

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("acks", "all");
        props.put("compression.type", "snappy");

        try (ProducerPool pool = new ProducerPool(bootstrapServers, topic, poolSize, props)) {
            IdempotentKafkaProducer.PerformanceResult result =
                pool.sendDistributed(totalMessages, 1024);

            assertEquals(totalMessages, result.successCount,
                "All messages should be produced successfully");

            log.info("Load balancing test: {} messages across {} producers in {} ms",
                totalMessages, poolSize, result.durationMs);
        }
    }

    @Test
    void testMultipleProducersNoMessageLoss() throws Exception {
        String topic = "mt-producer-loss-test-" + System.currentTimeMillis();
        int poolSize = 4;
        int totalMessages = 1000;

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("acks", "all");
        props.put("retries", 3);

        // Produce messages
        try (ProducerPool pool = new ProducerPool(bootstrapServers, topic, poolSize, props)) {
            IdempotentKafkaProducer.PerformanceResult result =
                pool.sendDistributed(totalMessages, 512);

            assertEquals(totalMessages, result.successCount,
                "All messages should be produced successfully");
        }

        // Consume and verify count
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "mt-producer-verifier-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        AtomicInteger receivedCount = new AtomicInteger(0);

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));

            int maxPolls = 20;
            int polls = 0;

            while (polls < maxPolls) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(3));
                receivedCount.addAndGet(records.count());

                if (records.isEmpty()) {
                    polls++;
                } else {
                    polls = 0;
                }
            }
        }

        assertEquals(totalMessages, receivedCount.get(),
            "Should have received exactly " + totalMessages + " messages");
        log.info("Verified: {} messages received without loss", receivedCount.get());
    }

    @Test
    void testProducerPoolScaling() throws Exception {
        String topic = "mt-scaling-test-" + System.currentTimeMillis();
        int messageCount = 500;

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("acks", "all");
        props.put("batch.size", "65536");
        props.put("linger.ms", "10");

        // Test with different pool sizes
        int[] poolSizes = {1, 2, 4};

        for (int poolSize : poolSizes) {
            String perTopic = topic + "-" + poolSize;
            try (ProducerPool pool = new ProducerPool(bootstrapServers, perTopic, poolSize, props)) {
                IdempotentKafkaProducer.PerformanceResult result =
                    pool.sendDistributed(messageCount, 512);

                log.info("Pool size {}: {} msg/s", poolSize, result.throughput);

                assertEquals(messageCount, result.successCount,
                    "Pool size " + poolSize + " should produce all messages");
            }
        }
    }
}
