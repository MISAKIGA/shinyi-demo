package com.shinyi.eventbus.demo.kafka.producer;

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
 * Thread-safe idempotent Kafka producer with exactly-once semantics.
 * Configuration ensures:
 * - enable.idempotence=true for exactly-once guarantee
 * - acks="all" required for idempotence
 * - retries=MAX_VALUE for reliability
 * - max.in.flight.requests.per.connection=5 (Kafka default, safe with idempotence)
 */
@Slf4j
public class IdempotentKafkaProducer implements AutoCloseable {

    private final KafkaProducer<String, byte[]> producer;
    private final String topic;
    private final boolean syncSend;

    public IdempotentKafkaProducer(String bootstrapServers, String topic, Properties overrides) {
        this.topic = topic;
        Properties props = buildIdempotentProperties(bootstrapServers, overrides);
        this.producer = new KafkaProducer<>(props);
        this.syncSend = false;
        log.info("IdempotentKafkaProducer initialized for topic: {}", topic);
    }

    public IdempotentKafkaProducer(String bootstrapServers, String topic, Properties overrides, boolean syncSend) {
        this.topic = topic;
        Properties props = buildIdempotentProperties(bootstrapServers, overrides);
        this.producer = new KafkaProducer<>(props);
        this.syncSend = syncSend;
        log.info("IdempotentKafkaProducer initialized for topic: {} (sync={})", topic, syncSend);
    }

    private Properties buildIdempotentProperties(String bootstrapServers, Properties overrides) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        // Idempotence configuration (CRITICAL for exactly-once)
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Apply overrides
        if (overrides != null) {
            props.putAll(overrides);
        }

        // Ensure idempotence is always enabled
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return props;
    }

    /**
     * Send a message asynchronously with callback.
     */
    public void sendAsync(String key, byte[] payload, SendCallback callback) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Failed to send message with key {}: {}", key, exception.getMessage());
            }
            callback.onComplete(metadata, exception);
        });
    }

    /**
     * Send a message synchronously and wait for acknowledgment.
     */
    public RecordMetadata sendSync(String key, byte[] payload) throws Exception {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload);
        return producer.send(record).get(30, TimeUnit.SECONDS);
    }

    /**
     * Send multiple messages with counting latch.
     */
    public PerformanceResult sendBatch(int messageCount, int messageSize, int batchSize, int lingerMs) {
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        AtomicLong totalBytes = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(messageCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            byte[] payload = generatePayload(messageSize, messageId);

            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, String.valueOf(messageId), payload);

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

            if (i > 0 && i % batchSize == 0) {
                producer.flush();
            }
        }

        try {
            latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        producer.flush();
        long duration = System.currentTimeMillis() - startTime;

        return buildResult(successCount.get(), failureCount.get(), totalBytes.get(), duration);
    }

    /**
     * Send multiple messages synchronously (blocking).
     */
    public PerformanceResult sendBatchSync(int messageCount, int messageSize) {
        long successCount = 0;
        long failureCount = 0;
        long totalBytes = 0;

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < messageCount; i++) {
            byte[] payload = generatePayload(messageSize, i);
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, String.valueOf(i), payload);

            try {
                producer.send(record).get(30, TimeUnit.SECONDS);
                successCount++;
                totalBytes += payload.length;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to send message {}: {}", i, e.getMessage());
            }

            if (i > 0 && i % 1000 == 0) {
                log.info("Sent {} messages...", i);
            }
        }

        producer.flush();
        long duration = System.currentTimeMillis() - startTime;

        return buildResult(successCount, failureCount, totalBytes, duration);
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

    private PerformanceResult buildResult(long successes, long failures, long bytes, long duration) {
        long durationSec = Math.max(duration / 1000, 1);
        double throughput = successes / (double) durationSec;
        double mbSent = bytes / (1024.0 * 1024.0);
        double mbPerSec = mbSent / durationSec;
        double avgLatencyMs = duration / (double) Math.max(successes, 1);

        PerformanceResult result = new PerformanceResult();
        result.successCount = successes;
        result.failureCount = failures;
        result.totalBytes = bytes;
        result.durationMs = duration;
        result.throughput = throughput;
        result.mbPerSec = mbPerSec;
        result.avgLatencyMs = avgLatencyMs;

        log.info("=== IdempotentProducer Performance Results ===");
        log.info("Successful: {}, Failed: {}", successes, failures);
        log.info("Duration: {} ms, Throughput: {} msg/sec", duration, String.format("%.2f", throughput));

        return result;
    }

    public void flush() {
        producer.flush();
    }

    @Override
    public void close() {
        producer.close();
        log.info("IdempotentKafkaProducer closed for topic: {}", topic);
    }

    public interface SendCallback {
        void onComplete(RecordMetadata metadata, Exception exception);
    }

    public static class PerformanceResult {
        public long successCount;
        public long failureCount;
        public long totalBytes;
        public long durationMs;
        public double throughput;
        public double mbPerSec;
        public double avgLatencyMs;

        @Override
        public String toString() {
            return String.format("PerformanceResult{success=%d, failed=%d, throughput=%.2f msg/s, latency=%.2f ms}",
                    successCount, failureCount, throughput, avgLatencyMs);
        }
    }
}
