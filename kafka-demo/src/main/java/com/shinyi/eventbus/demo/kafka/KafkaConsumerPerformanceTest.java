package com.shinyi.eventbus.demo.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka Consumer Performance Test
 * Tests consumer throughput with configurable parameters
 */
@Slf4j
public class KafkaConsumerPerformanceTest {

    private final String bootstrapServers;
    private final String topic;
    private final String groupId;

    public KafkaConsumerPerformanceTest(String bootstrapServers, String topic, String groupId) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.groupId = groupId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String bootstrapServers = "localhost:9092";
        private String topic = "test-topic";
        private String groupId = "test-group";
        private String autoOffsetReset = "earliest";
        private boolean enableAutoCommit = true;
        private int sessionTimeoutMs = 45000;
        private int maxPollRecords = 500;
        private int maxPollIntervalMs = 300000;
        private String securityProtocol = "PLAINTEXT";
        private String saslMechanism = "PLAIN";
        private String username = null;
        private String password = null;

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder autoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
            return this;
        }

        public Builder enableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
            return this;
        }

        public Builder sessionTimeoutMs(int sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
            return this;
        }

        public Builder maxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
            return this;
        }

        public Builder maxPollIntervalMs(int maxPollIntervalMs) {
            this.maxPollIntervalMs = maxPollIntervalMs;
            return this;
        }

        public Builder securityProtocol(String securityProtocol) {
            this.securityProtocol = securityProtocol;
            return this;
        }

        public Builder saslMechanism(String saslMechanism) {
            this.saslMechanism = saslMechanism;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public KafkaConsumerPerformanceTest build() {
            return new KafkaConsumerPerformanceTest(bootstrapServers, topic, groupId);
        }
    }

    private Properties buildConsumerProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

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

        return props;
    }

    public PerformanceResult runConsumeTest(int expectedMessages, int timeoutSeconds) throws Exception {
        log.info("Starting Kafka Consumer Performance Test");
        log.info("Bootstrap Servers: {}", bootstrapServers);
        log.info("Topic: {}", topic);
        log.info("Group ID: {}", groupId);
        log.info("Expected Messages: {}", expectedMessages);
        log.info("Timeout: {} seconds", timeoutSeconds);
        log.info("Security Protocol: {}", System.getProperty("kafka.security.protocol", "PLAINTEXT"));

        Properties props = buildConsumerProperties();

        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        AtomicLong totalBytes = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(expectedMessages);

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            long startTime = System.currentTimeMillis();
            long endTime = startTime + (timeoutSeconds * 1000L);

            while (System.currentTimeMillis() < endTime && latch.getCount() > 0) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, byte[]> record : records) {
                    try {
                        if (record.value() != null && record.value().length > 0) {
                            successCount.incrementAndGet();
                            totalBytes.addAndGet(record.value().length);
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

            long duration = System.currentTimeMillis() - startTime;

            // Wait a bit more for any remaining messages
            if (latch.getCount() > 0) {
                log.info("Waiting for remaining {} messages...", latch.getCount());
                latch.await(timeoutSeconds, TimeUnit.SECONDS);
            }

            return calculateResults(successCount, failureCount, totalBytes, duration);
        }
    }

    public void runConsumeAndPrint(int maxMessages, int pollTimeoutMs) throws Exception {
        log.info("Starting Kafka Consumer - Print Mode");
        log.info("Bootstrap Servers: {}", bootstrapServers);
        log.info("Topic: {}", topic);
        log.info("Group ID: {}", groupId);
        log.info("Max Messages: {}", maxMessages);

        Properties props = buildConsumerProperties();

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            int count = 0;
            while (count < maxMessages) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));

                for (ConsumerRecord<String, byte[]> record : records) {
                    String key = record.key() != null ? record.key() : "null";
                    String value = new String(record.value());
                    log.info("Received: key={}, offset={}, partition={}, value={}",
                            key, record.offset(), record.partition(),
                            value.length() > 100 ? value.substring(0, 100) + "..." : value);
                    count++;

                    if (count >= maxMessages) {
                        break;
                    }
                }
            }

            log.info("Total messages received: {}", count);
        }
    }

    private PerformanceResult calculateResults(AtomicLong successCount, AtomicLong failureCount,
                                               AtomicLong totalBytes, long duration) {
        long successes = successCount.get();
        long failures = failureCount.get();
        long bytes = totalBytes.get();
        long durationSec = Math.max(duration / 1000, 1);

        double throughput = successes / (double) durationSec;
        double mbReceived = bytes / (1024.0 * 1024.0);
        double mbPerSec = mbReceived / durationSec;
        double avgLatencyMs = duration / (double) Math.max(successes, 1);

        PerformanceResult result = new PerformanceResult();
        result.setSuccessCount(successes);
        result.setFailureCount(failures);
        result.setTotalBytes(bytes);
        result.setDurationMs(duration);
        result.setThroughput(throughput);
        result.setMbPerSec(mbPerSec);
        result.setAvgLatencyMs(avgLatencyMs);

        log.info("=== Consumer Performance Results ===");
        log.info("Total Messages: {}", successes + failures);
        log.info("Successful: {}, Failed: {}", successes, failures);
        log.info("Total Data: {} MB", String.format("%.2f", mbReceived));
        log.info("Duration: {} ms", duration);
        log.info("Throughput: {} msg/sec", String.format("%.2f", throughput));
        log.info("Bandwidth: {} MB/sec", String.format("%.2f", mbPerSec));
        log.info("Avg Latency: {} ms", String.format("%.2f", avgLatencyMs));

        return result;
    }

    @lombok.Data
    public static class PerformanceResult {
        private long successCount;
        private long failureCount;
        private long totalBytes;
        private long durationMs;
        private double throughput;
        private double mbPerSec;
        private double avgLatencyMs;
    }
}
