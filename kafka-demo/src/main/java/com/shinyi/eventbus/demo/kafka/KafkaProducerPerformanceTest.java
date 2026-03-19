package com.shinyi.eventbus.demo.kafka;

import com.shinyi.eventbus.EventModel;
import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka Producer Performance Test
 * Tests producer throughput with configurable message size and count
 */
@Slf4j
public class KafkaProducerPerformanceTest {

    private final KafkaConnectConfig config;
    private final String bootstrapServers;

    public KafkaProducerPerformanceTest(String bootstrapServers, KafkaConnectConfig config) {
        this.bootstrapServers = bootstrapServers;
        this.config = config;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String bootstrapServers = "localhost:9092";
        private String topic = "test-topic";
        private String acks = "1";
        private int retries = 3;
        private int batchSize = 16384;
        private int lingerMs = 1;
        private int bufferMemory = 33554432;
        private int messageSize = 1024;
        private int messageCount = 10000;
        private String securityProtocol = "PLAINTEXT";
        private String saslMechanism = "PLAIN";
        private String username = null;
        private String password = null;
        private String kerberosServiceName = "kafka";
        private String kerberosPrincipal = null;
        private String kerberosKeytab = null;
        private String kerberosKrb5Location = null;

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder acks(String acks) {
            this.acks = acks;
            return this;
        }

        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder lingerMs(int lingerMs) {
            this.lingerMs = lingerMs;
            return this;
        }

        public Builder bufferMemory(int bufferMemory) {
            this.bufferMemory = bufferMemory;
            return this;
        }

        public Builder messageSize(int messageSize) {
            this.messageSize = messageSize;
            return this;
        }

        public Builder messageCount(int messageCount) {
            this.messageCount = messageCount;
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

        public Builder kerberosServiceName(String kerberosServiceName) {
            this.kerberosServiceName = kerberosServiceName;
            return this;
        }

        public Builder kerberosPrincipal(String kerberosPrincipal) {
            this.kerberosPrincipal = kerberosPrincipal;
            return this;
        }

        public Builder kerberosKeytab(String kerberosKeytab) {
            this.kerberosKeytab = kerberosKeytab;
            return this;
        }

        public Builder kerberosKrb5Location(String kerberosKrb5Location) {
            this.kerberosKrb5Location = kerberosKrb5Location;
            return this;
        }

        public KafkaProducerPerformanceTest build() {
            KafkaConnectConfig config = new KafkaConnectConfig();
            config.setTopic(topic);
            config.setAcks(acks);
            config.setRetries(retries);
            config.setBatchSize(batchSize);
            config.setLingerMs(lingerMs);
            config.setBufferMemory(bufferMemory);
            return new KafkaProducerPerformanceTest(bootstrapServers, config);
        }
    }

    private Properties buildProducerProperties() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, config.getAcks());
        props.put(ProducerConfig.RETRIES_CONFIG, config.getRetries());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, config.getBatchSize());
        props.put(ProducerConfig.LINGER_MS_CONFIG, config.getLingerMs());
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, config.getBufferMemory());
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Security settings
        String securityProtocol = System.getProperty("kafka.security.protocol", "PLAINTEXT");
        props.put("security.protocol", securityProtocol);

        if ("SASL_PLAINTEXT".equals(securityProtocol) || "SASL_SSL".equals(securityProtocol)) {
            String saslMechanism = System.getProperty("kafka.sasl.mechanism", "PLAIN");
            props.put("sasl.mechanism", saslMechanism);

            if ("PLAIN".equals(saslMechanism)) {
                String jaasTemplate = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                        "username=\"%s\" password=\"%s\";";
                props.put("sasl.jaas.config", String.format(jaasTemplate,
                        System.getProperty("kafka.sasl.username", "admin"),
                        System.getProperty("kafka.sasl.password", "admin-secret")));
            } else if ("SCRAM-SHA-256".equals(saslMechanism) || "SCRAM-SHA-512".equals(saslMechanism)) {
                String jaasTemplate = "org.apache.kafka.common.security.scram.ScramLoginModule required " +
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
                String krb5Location = System.getProperty("java.security.krb5.conf");
                if (krb5Location != null) {
                    props.put("java.security.krb5.conf", krb5Location);
                }
            }
        }

        return props;
    }

    public PerformanceResult runPerformanceTest(int messageCount, int messageSize) throws Exception {
        log.info("Starting Kafka Producer Performance Test");
        log.info("Bootstrap Servers: {}", bootstrapServers);
        log.info("Topic: {}", config.getTopic());
        log.info("Message Count: {}", messageCount);
        log.info("Message Size: {} bytes", messageSize);
        log.info("Security Protocol: {}", System.getProperty("kafka.security.protocol", "PLAINTEXT"));

        Properties props = buildProducerProperties();

        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        AtomicLong totalBytes = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(messageCount);

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < messageCount; i++) {
                final int messageId = i;
                byte[] payload = generatePayload(messageSize, messageId);

                ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                        config.getTopic(),
                        String.valueOf(messageId),
                        payload
                );

                producer.send(record, (metadata, exception) -> {
                    if (exception == null) {
                        successCount.incrementAndGet();
                        totalBytes.addAndGet(payload.length);
                    } else {
                        failureCount.incrementAndGet();
                        log.error("Failed to send message {}: {}", messageId, exception.getMessage());
                    }
                    latch.countDown();
                });

                if (i % 1000 == 0 && i > 0) {
                    log.info("Sent {} messages...", i);
                }
            }

            latch.await(5, TimeUnit.MINUTES);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            producer.flush();

            return calculateResults(successCount, failureCount, totalBytes, duration);
        }
    }

    public PerformanceResult runSyncPerformanceTest(int messageCount, int messageSize) throws Exception {
        log.info("Starting Kafka Producer Sync Performance Test");
        log.info("Bootstrap Servers: {}", bootstrapServers);
        log.info("Topic: {}", config.getTopic());
        log.info("Message Count: {}", messageCount);
        log.info("Message Size: {} bytes", messageSize);

        Properties props = buildProducerProperties();

        long successCount = 0;
        long failureCount = 0;
        long totalBytes = 0;

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < messageCount; i++) {
                byte[] payload = generatePayload(messageSize, i);
                ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                        config.getTopic(),
                        String.valueOf(i),
                        payload
                );

                try {
                    RecordMetadata metadata = producer.send(record).get();
                    successCount++;
                    totalBytes += payload.length;
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to send message {}: {}", i, e.getMessage());
                }

                if (i % 1000 == 0 && i > 0) {
                    log.info("Sent {} messages...", i);
                }
            }

            producer.flush();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            return calculateResults(successCount, failureCount, totalBytes, duration);
        }
    }

    private byte[] generatePayload(int size, int id) {
        byte[] payload = new byte[size];
        String prefix = "MSG-" + id + "-";
        byte[] prefixBytes = prefix.getBytes();
        System.arraycopy(prefixBytes, 0, payload, 0, Math.min(prefixBytes.length, size));
        for (int i = prefixBytes.length; i < size; i++) {
            payload[i] = (byte) ('A' + (i % 26));
        }
        return payload;
    }

    private PerformanceResult calculateResults(AtomicLong successCount, AtomicLong failureCount,
                                               AtomicLong totalBytes, long duration) {
        long successes = successCount.get();
        long failures = failureCount.get();
        long bytes = totalBytes.get();
        long durationSec = Math.max(duration / 1000, 1);

        double throughput = successes / (double) durationSec;
        double mbSent = bytes / (1024.0 * 1024.0);
        double mbPerSec = mbSent / durationSec;
        double avgLatencyMs = duration / (double) Math.max(successes, 1);

        PerformanceResult result = new PerformanceResult();
        result.setSuccessCount(successes);
        result.setFailureCount(failures);
        result.setTotalBytes(bytes);
        result.setDurationMs(duration);
        result.setThroughput(throughput);
        result.setMbPerSec(mbPerSec);
        result.setAvgLatencyMs(avgLatencyMs);

        log.info("=== Producer Performance Results ===");
        log.info("Total Messages: {}", successes + failures);
        log.info("Successful: {}, Failed: {}", successes, failures);
        log.info("Total Data: {} MB", String.format("%.2f", mbSent));
        log.info("Duration: {} ms", duration);
        log.info("Throughput: {} msg/sec", String.format("%.2f", throughput));
        log.info("Bandwidth: {} MB/sec", String.format("%.2f", mbPerSec));
        log.info("Avg Latency: {} ms", String.format("%.2f", avgLatencyMs));

        return result;
    }

    private PerformanceResult calculateResults(long successCount, long failureCount,
                                               long totalBytes, long duration) {
        long durationSec = Math.max(duration / 1000, 1);

        double throughput = successCount / (double) durationSec;
        double mbSent = totalBytes / (1024.0 * 1024.0);
        double mbPerSec = mbSent / durationSec;
        double avgLatencyMs = duration / (double) Math.max(successCount, 1);

        PerformanceResult result = new PerformanceResult();
        result.setSuccessCount(successCount);
        result.setFailureCount(failureCount);
        result.setTotalBytes(totalBytes);
        result.setDurationMs(duration);
        result.setThroughput(throughput);
        result.setMbPerSec(mbPerSec);
        result.setAvgLatencyMs(avgLatencyMs);

        log.info("=== Producer Performance Results ===");
        log.info("Total Messages: {}", successCount + failureCount);
        log.info("Successful: {}, Failed: {}", successCount, failureCount);
        log.info("Total Data: {} MB", String.format("%.2f", mbSent));
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
