package com.shinyi.eventbus.demo.kafka.integration;

import com.shinyi.eventbus.demo.kafka.consumer.ConsumerPool;
import com.shinyi.eventbus.demo.kafka.consumer.ManualOffsetConsumer;
import com.shinyi.eventbus.demo.kafka.producer.IdempotentKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for multi-threaded consumer safety.
 */
@Slf4j
public class MultiThreadedConsumerTest extends BaseIntegrationTest {

    @Test
    void testConsumerPoolParallelConsumption() throws Exception {
        String topic = "mt-consumer-test-" + System.currentTimeMillis();
        String groupIdBase = "mt-consumer-group-" + System.currentTimeMillis();
        int poolSize = 2;
        int messageCount = 500;

        // First produce messages
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("acks", "all");

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, producerProps)) {
            producer.sendBatch(messageCount, 512, 250, 5);
        }

        Thread.sleep(1000); // Wait for messages

        // Consume with pool
        Properties consumerProps = new Properties();
        consumerProps.put("enable.auto.commit", "false");

        try (ConsumerPool pool = new ConsumerPool(
                bootstrapServers, topic, groupIdBase, poolSize, consumerProps, 1000)) {

            ConsumerPool.MultiConsumerResult result =
                pool.consumeParallel(messageCount, 512, 60);

            log.info("ConsumerPool result: success={}, failed={}, throughput={}",
                result.successCount, result.failureCount, result.throughput);

            // Should consume at least the number of messages produced
            assertTrue(result.successCount >= messageCount,
                "Should consume at least " + messageCount + " messages, got " + result.successCount);
        }
    }

    @Test
    void testConsumerPoolMessageCountAccuracy() throws Exception {
        String topic = "mt-consumer-accuracy-test-" + System.currentTimeMillis();
        String groupIdBase = "mt-accuracy-group-" + System.currentTimeMillis();
        int poolSize = 2;
        int messageCount = 300;

        // Produce messages
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("acks", "all");

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, producerProps)) {
            producer.sendBatch(messageCount, 512, 150, 5);
        }

        Thread.sleep(1000);

        // Consume with pool
        Properties consumerProps = new Properties();
        consumerProps.put("enable.auto.commit", "false");
        consumerProps.put("max.poll.records", "100");

        try (ConsumerPool pool = new ConsumerPool(
                bootstrapServers, topic, groupIdBase, poolSize, consumerProps, 1000)) {

            ConsumerPool.MultiConsumerResult result =
                pool.consumeParallel(messageCount, 512, 60);

            log.info("ConsumerPool consumed: {} messages", result.successCount);

            // Should consume at least the number of messages produced
            assertTrue(result.successCount >= messageCount,
                "Should consume at least " + messageCount + " messages");
        }
    }

    @Test
    void testConsumerPoolWithEos() throws Exception {
        String topic = "mt-consumer-eos-test-" + System.currentTimeMillis();
        String groupIdBase = "mt-eos-group-" + System.currentTimeMillis();
        int poolSize = 2;
        int messageCount = 200;

        // Produce messages
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("acks", "all");
        producerProps.put("enable.idempotence", true);

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, producerProps)) {
            producer.sendBatch(messageCount, 512, 100, 5);
        }

        Thread.sleep(1000);

        // Consume with EOS semantics
        Properties consumerProps = new Properties();
        consumerProps.put("enable.auto.commit", "false");
        consumerProps.put("max.poll.records", "100");

        ManualOffsetConsumer.MessageProcessor processor = (key, value) -> {
            return value != null && value.length > 0;
        };

        try (ConsumerPool pool = new ConsumerPool(
                bootstrapServers, topic, groupIdBase, poolSize, consumerProps, 1000)) {

            ConsumerPool.MultiConsumerResult result =
                pool.consumeWithEos(messageCount, 512, 60, processor);

            log.info("EOS ConsumerPool result: success={}, throughput={}",
                result.successCount, result.throughput);

            // Should consume at least the number of messages produced
            assertTrue(result.successCount >= messageCount,
                "Should consume at least " + messageCount + " messages with EOS");
        }
    }

    @Test
    void testConsumerPoolScaling() throws Exception {
        String topicBase = "mt-consumer-scaling-test-" + System.currentTimeMillis();
        String groupIdBase = "mt-scaling-group-" + System.currentTimeMillis();
        int messageCount = 300;

        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("acks", "all");

        int[] poolSizes = {1, 2};

        for (int poolSize : poolSizes) {
            String topic = topicBase + "-" + poolSize;

            // Produce messages
            try (IdempotentKafkaProducer producer =
                 new IdempotentKafkaProducer(bootstrapServers, topic, producerProps)) {
                producer.sendBatch(messageCount, 512, 150, 5);
            }

            Thread.sleep(500);

            // Consume with different pool sizes
            Properties consumerProps = new Properties();
            consumerProps.put("enable.auto.commit", "false");
            consumerProps.put("max.poll.records", "100");

            try (ConsumerPool pool = new ConsumerPool(
                    bootstrapServers, topic, groupIdBase + poolSize, poolSize, consumerProps, 1000)) {

                ConsumerPool.MultiConsumerResult result =
                    pool.consumeParallel(messageCount, 512, 60);

                log.info("Pool size {}: consumed={}, throughput={}",
                    poolSize, result.successCount, result.throughput);

                // Should consume at least the number of messages produced
                assertTrue(result.successCount >= messageCount,
                    "Pool size " + poolSize + " should consume at least " + messageCount + " messages");
            }
        }
    }

    @Test
    void testSingleConsumerWithManualCommit() throws Exception {
        String topic = "single-consumer-eos-test-" + System.currentTimeMillis();
        String groupId = "single-eos-group-" + System.currentTimeMillis();
        int messageCount = 100;

        // Produce messages
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", bootstrapServers);
        producerProps.put("acks", "all");

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, producerProps)) {
            producer.sendBatch(messageCount, 512, 50, 5);
        }

        Thread.sleep(500);

        // Consume with EOS
        Properties consumerProps = new Properties();
        consumerProps.put("enable.auto.commit", "false");

        AtomicInteger processedCount = new AtomicInteger(0);

        try (ManualOffsetConsumer consumer =
             new ManualOffsetConsumer(bootstrapServers, topic, groupId, consumerProps, 1000)) {

            ManualOffsetConsumer.ConsumptionResult result =
                consumer.consumeWithManualCommit(messageCount, 30,
                    (key, value) -> {
                        processedCount.incrementAndGet();
                        return true;
                    });

            assertEquals(messageCount, result.successCount,
                "Should consume exactly " + messageCount + " messages");
        }
    }
}
