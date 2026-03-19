package com.shinyi.eventbus.demo.kafka.consumer;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-threaded consumer pool using ManualOffsetConsumer for exactly-once consumption.
 * Each thread gets its own consumer instance to enable true parallel consumption from multiple partitions.
 */
@Slf4j
public class ConsumerPool implements AutoCloseable {

    private final List<ManualOffsetConsumer> consumers;
    private final ExecutorService executorService;
    private final int poolSize;
    private final AtomicLong consumedCount;

    public ConsumerPool(String bootstrapServers, String topic, String groupIdBase,
                       int poolSize, Properties overrides, int pollTimeoutMs) {
        this.poolSize = poolSize;
        this.consumers = new ArrayList<>(poolSize);
        this.executorService = Executors.newFixedThreadPool(poolSize);
        this.consumedCount = new AtomicLong(0);

        // Create one consumer per thread
        for (int i = 0; i < poolSize; i++) {
            String groupId = groupIdBase + "-" + i;
            consumers.add(new ManualOffsetConsumer(bootstrapServers, topic, groupId, overrides, pollTimeoutMs));
        }

        log.info("ConsumerPool initialized with {} consumers for topic: {}", poolSize, topic);
    }

    /**
     * Consume messages across all consumers in parallel.
     */
    public MultiConsumerResult consumeParallel(int totalMessages, int messageSize,
                                              int timeoutSeconds) throws Exception {
        int messagesPerConsumer = totalMessages / poolSize;
        int remainingMessages = totalMessages % poolSize;

        MultiConsumerResult aggregatedResult = new MultiConsumerResult();
        long startTime = System.currentTimeMillis();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            final int consumerIndex = i;
            int messagesForThisConsumer = messagesPerConsumer + (i < remainingMessages ? 1 : 0);

            Thread thread = new Thread(() -> {
                ManualOffsetConsumer consumer = consumers.get(consumerIndex);
                ManualOffsetConsumer.ConsumptionResult result =
                    consumer.consumeSimple(messagesForThisConsumer, timeoutSeconds);

                synchronized (aggregatedResult) {
                    aggregatedResult.successCount += result.successCount;
                    aggregatedResult.failureCount += result.failureCount;
                    aggregatedResult.totalBytes += result.totalBytes;
                }
            });
            threads.add(thread);
        }

        // Start all consumers
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all consumers to complete
        for (Thread thread : threads) {
            thread.join();
        }

        long duration = System.currentTimeMillis() - startTime;
        aggregatedResult.durationMs = duration;
        aggregatedResult.throughput = aggregatedResult.successCount / (double) Math.max(duration / 1000, 1);
        aggregatedResult.mbPerSec = (aggregatedResult.totalBytes / (1024.0 * 1024.0)) / Math.max(duration / 1000.0, 1);
        aggregatedResult.avgLatencyMs = duration / (double) Math.max(aggregatedResult.successCount, 1);

        log.info("=== ConsumerPool Performance Results ===");
        log.info("Pool Size: {}", poolSize);
        log.info("Total Messages: {}", aggregatedResult.successCount + aggregatedResult.failureCount);
        log.info("Successful: {}, Failed: {}", aggregatedResult.successCount, aggregatedResult.failureCount);
        log.info("Duration: {} ms, Throughput: {} msg/sec",
                duration, String.format("%.2f", aggregatedResult.throughput));

        return aggregatedResult;
    }

    /**
     * Consume messages with exactly-once semantics (manual commit after processing).
     */
    public MultiConsumerResult consumeWithEos(int totalMessages, int messageSize,
                                              int timeoutSeconds,
                                              ManualOffsetConsumer.MessageProcessor processor) throws Exception {
        int messagesPerConsumer = totalMessages / poolSize;
        int remainingMessages = totalMessages % poolSize;

        MultiConsumerResult aggregatedResult = new MultiConsumerResult();
        long startTime = System.currentTimeMillis();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            final int consumerIndex = i;
            int messagesForThisConsumer = messagesPerConsumer + (i < remainingMessages ? 1 : 0);

            Thread thread = new Thread(() -> {
                try {
                    ManualOffsetConsumer consumer = consumers.get(consumerIndex);
                    ManualOffsetConsumer.ConsumptionResult result =
                        consumer.consumeWithManualCommit(messagesForThisConsumer, timeoutSeconds, processor);

                    synchronized (aggregatedResult) {
                        aggregatedResult.successCount += result.successCount;
                        aggregatedResult.failureCount += result.failureCount;
                        aggregatedResult.totalBytes += result.totalBytes;
                    }
                } catch (Exception e) {
                    log.error("Consumer {} error: {}", consumerIndex, e.getMessage());
                }
            });
            threads.add(thread);
        }

        // Start all consumers
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all consumers to complete
        for (Thread thread : threads) {
            thread.join();
        }

        long duration = System.currentTimeMillis() - startTime;
        aggregatedResult.durationMs = duration;
        aggregatedResult.throughput = aggregatedResult.successCount / (double) Math.max(duration / 1000, 1);
        aggregatedResult.mbPerSec = (aggregatedResult.totalBytes / (1024.0 * 1024.0)) / Math.max(duration / 1000.0, 1);
        aggregatedResult.avgLatencyMs = duration / (double) Math.max(aggregatedResult.successCount, 1);

        log.info("=== ConsumerPool EOS Performance Results ===");
        log.info("Pool Size: {}, Total Consumed: {}", poolSize, aggregatedResult.successCount);

        return aggregatedResult;
    }

    /**
     * Execute a custom consumer task on each consumer in the pool.
     */
    public void executeOnEach(ConsumerTask task) {
        executorService.submit(() -> {
            for (ManualOffsetConsumer consumer : consumers) {
                task.accept(consumer);
            }
        });
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdown();
        for (ManualOffsetConsumer consumer : consumers) {
            consumer.close();
        }
        log.info("ConsumerPool closed");
    }

    public int getPoolSize() {
        return poolSize;
    }

    @FunctionalInterface
    public interface ConsumerTask {
        void accept(ManualOffsetConsumer consumer);
    }

    public static class MultiConsumerResult {
        public long successCount;
        public long failureCount;
        public long totalBytes;
        public long durationMs;
        public double throughput;
        public double mbPerSec;
        public double avgLatencyMs;

        @Override
        public String toString() {
            return String.format("MultiConsumerResult{success=%d, failed=%d, throughput=%.2f msg/s}",
                    successCount, failureCount, throughput);
        }
    }
}
