package com.shinyi.eventbus.demo.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka consumer with manual offset commit for exactly-once semantics.
 * Features:
 * - ENABLE_AUTO_COMMIT=false
 * - commitSync() after successful processing
 * - OffsetAndMetadata(offset + 1) marks the next position to consume
 */
@Slf4j
public class ManualOffsetConsumer implements AutoCloseable {

    private final String bootstrapServers;
    private final String topic;
    private final String groupId;
    private final KafkaConsumer<String, byte[]> consumer;
    private final int pollTimeoutMs;

    public ManualOffsetConsumer(String bootstrapServers, String topic, String groupId,
                                Properties overrides, int pollTimeoutMs) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.groupId = groupId;
        this.pollTimeoutMs = pollTimeoutMs;

        Properties props = buildConsumerProperties(overrides);
        this.consumer = new KafkaConsumer<>(props);

        log.info("ManualOffsetConsumer initialized for topic: {}, groupId: {}", topic, groupId);
    }

    private Properties buildConsumerProperties(Properties overrides) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // CRITICAL: Disable auto commit for exactly-once semantics
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 5000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 1000);
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 10485760);

        // Security settings
        String securityProtocol = System.getProperty("kafka.security.protocol", "PLAINTEXT");
        props.put("security.protocol", securityProtocol);

        if ("SASL_PLAINTEXT".equals(securityProtocol) || "SASL_SSL".equals(securityProtocol)) {
            String saslMechanism = System.getProperty("kafka.sasl.mechanism", "PLAIN");
            props.put("sasl.mechanism", saslMechanism);

            if ("PLAIN".equals(saslMechanism) || "SCRAM-SHA-256".equals(saslMechanism) || "SCRAM-SHA-512".equals(saslMechanism)) {
                String jaasTemplate = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                        "username=\"%s\" password=\"%s\";";
                props.put("sasl.jaas.config", String.format(jaasTemplate,
                        System.getProperty("kafka.sasl.username", "admin"),
                        System.getProperty("kafka.sasl.password", "admin-secret")));
            } else if ("GSSAPI".equals(saslMechanism)) {
                String jaasTemplate = "com.sun.security.auth.module.Krb5LoginModule required " +
                        "useKeyTab=true keyTab=\"%s\" storeKey=true " +
                        "serviceName=\"%s\" principal=\"%s\";";
                props.put("sasl.jaas.config", String.format(jaasTemplate,
                        System.getProperty("kafka.kerberos.keytab", "/etc/kafka/kafka.keytab"),
                        System.getProperty("kafka.kerberos.service.name", "kafka"),
                        System.getProperty("kafka.kerberos.principal", "kafka/kafka.example.com@EXAMPLE.COM")));
            }
        }

        // Apply overrides
        if (overrides != null) {
            props.putAll(overrides);
        }

        // Ensure auto commit is disabled
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return props;
    }

    /**
     * Consume messages with manual offset commit after successful processing.
     * Returns the count of successfully consumed messages.
     */
    public ConsumptionResult consumeWithManualCommit(int expectedMessages, int timeoutSeconds,
                                                     MessageProcessor processor) throws Exception {
        log.info("Starting manual offset commit consumption");
        log.info("Expected messages: {}, Timeout: {} seconds", expectedMessages, timeoutSeconds);

        consumer.subscribe(Collections.singletonList(topic));

        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        AtomicLong totalBytes = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(expectedMessages);

        Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < endTime && latch.getCount() > 0) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));

            for (ConsumerRecord<String, byte[]> record : records) {
                try {
                    // Process the message
                    boolean processed = processor.process(record.key(), record.value());

                    if (processed) {
                        successCount.incrementAndGet();
                        totalBytes.addAndGet(record.value().length);

                        // Commit offset after successful processing
                        // offset + 1 marks the next position to consume
                        currentOffsets.put(
                            new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset() + 1, "processed")
                        );

                        // Commit periodically, not every message (for performance)
                        if (currentOffsets.size() >= 100) {
                            commitOffsets(currentOffsets);
                            currentOffsets.clear();
                        }
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("Error processing record at offset {}: {}",
                            record.offset(), e.getMessage());
                } finally {
                    latch.countDown();
                }
            }

            if (successCount.get() % 1000 == 0 && successCount.get() > 0) {
                log.info("Consumed {} messages...", successCount.get());
            }
        }

        // Commit any remaining offsets
        if (!currentOffsets.isEmpty()) {
            commitOffsets(currentOffsets);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Wait for remaining
        if (latch.getCount() > 0) {
            log.info("Waiting for remaining {} messages...", latch.getCount());
            latch.await(timeoutSeconds, TimeUnit.SECONDS);
        }

        return buildResult(successCount.get(), failureCount.get(), totalBytes.get(), duration);
    }

    /**
     * Simple consume with auto commit disabled (no processing tracking).
     */
    public ConsumptionResult consumeSimple(int expectedMessages, int timeoutSeconds) {
        log.info("Starting simple manual offset consumption");
        log.info("Expected messages: {}, Timeout: {} seconds", expectedMessages, timeoutSeconds);

        consumer.subscribe(Collections.singletonList(topic));

        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        AtomicLong totalBytes = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(expectedMessages);

        Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < endTime && latch.getCount() > 0) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));

            for (ConsumerRecord<String, byte[]> record : records) {
                try {
                    if (record.value() != null && record.value().length > 0) {
                        successCount.incrementAndGet();
                        totalBytes.addAndGet(record.value().length);

                        // Track offset
                        currentOffsets.put(
                            new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset() + 1, "consumed")
                        );

                        // Batch commit
                        if (currentOffsets.size() >= 100) {
                            commitOffsets(currentOffsets);
                            currentOffsets.clear();
                        }
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("Error processing record: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            }

            if (successCount.get() % 1000 == 0 && successCount.get() > 0) {
                log.info("Consumed {} messages...", successCount.get());
            }
        }

        // Final commit
        if (!currentOffsets.isEmpty()) {
            commitOffsets(currentOffsets);
        }

        long duration = System.currentTimeMillis() - startTime;

        return buildResult(successCount.get(), failureCount.get(), totalBytes.get(), duration);
    }

    private void commitOffsets(Map<TopicPartition, OffsetAndMetadata> offsets) {
        try {
            consumer.commitSync(offsets);
            log.debug("Committed offsets for {} partitions", offsets.size());
        } catch (Exception e) {
            log.error("Failed to commit offsets: {}", e.getMessage());
        }
    }

    private ConsumptionResult buildResult(long successes, long failures, long bytes, long duration) {
        long durationSec = Math.max(duration / 1000, 1);
        double throughput = successes / (double) durationSec;
        double mbReceived = bytes / (1024.0 * 1024.0);
        double mbPerSec = mbReceived / durationSec;
        double avgLatencyMs = duration / (double) Math.max(successes, 1);

        ConsumptionResult result = new ConsumptionResult();
        result.successCount = successes;
        result.failureCount = failures;
        result.totalBytes = bytes;
        result.durationMs = duration;
        result.throughput = throughput;
        result.mbPerSec = mbPerSec;
        result.avgLatencyMs = avgLatencyMs;

        log.info("=== ManualOffsetConsumer Results ===");
        log.info("Successful: {}, Failed: {}", successes, failures);
        log.info("Duration: {} ms, Throughput: {} msg/sec",
                duration, String.format("%.2f", throughput));

        return result;
    }

    public void close() {
        consumer.close();
        log.info("ManualOffsetConsumer closed for topic: {}, groupId: {}", topic, groupId);
    }

    @FunctionalInterface
    public interface MessageProcessor {
        /**
         * Process a message. Return true if processed successfully.
         */
        boolean process(String key, byte[] value);
    }

    public static class ConsumptionResult {
        public long successCount;
        public long failureCount;
        public long totalBytes;
        public long durationMs;
        public double throughput;
        public double mbPerSec;
        public double avgLatencyMs;

        @Override
        public String toString() {
            return String.format("ConsumptionResult{success=%d, failed=%d, throughput=%.2f msg/s}",
                    successCount, failureCount, throughput);
        }
    }
}
