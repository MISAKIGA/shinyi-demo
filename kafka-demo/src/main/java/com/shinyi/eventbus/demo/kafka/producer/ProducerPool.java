package com.shinyi.eventbus.demo.kafka.producer;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe producer pool using ExecutorService for multi-threaded message production.
 * Each thread gets its own IdempotentKafkaProducer instance for true parallelism.
 * Uses round-robin distribution for load balancing.
 */
@Slf4j
public class ProducerPool implements AutoCloseable {

    private final List<IdempotentKafkaProducer> producers;
    private final ExecutorService executorService;
    private final int poolSize;
    private final AtomicLong roundRobinCounter;

    public ProducerPool(String bootstrapServers, String topic, int poolSize, Properties overrides) {
        this.poolSize = poolSize;
        this.producers = new ArrayList<>(poolSize);
        this.roundRobinCounter = new AtomicLong(0);

        // Create fixed thread pool
        this.executorService = Executors.newFixedThreadPool(poolSize);

        // Create one producer per thread
        for (int i = 0; i < poolSize; i++) {
            producers.add(new IdempotentKafkaProducer(bootstrapServers, topic, overrides));
        }

        log.info("ProducerPool initialized with {} producers for topic: {}", poolSize, topic);
    }

    /**
     * Get the next producer in round-robin fashion.
     */
    private IdempotentKafkaProducer getNextProducer() {
        int index = (int) (roundRobinCounter.getAndIncrement() % poolSize);
        return producers.get(index);
    }

    /**
     * Execute a task on the producer pool using round-robin distribution.
     */
    public void execute(Runnable task) {
        executorService.submit(task);
    }

    /**
     * Submit a task and return the result of the producer's performance test.
     */
    public void submitSendTask(int messageCount, int messageSize,
                               IdempotentKafkaProducer.PerformanceResult result,
                               AtomicLong successCount, AtomicLong failureCount) {
        executorService.submit(() -> {
            IdempotentKafkaProducer producer = getNextProducer();
            IdempotentKafkaProducer.PerformanceResult batchResult =
                producer.sendBatch(messageCount, messageSize, 1000, 10);

            synchronized (result) {
                result.successCount += batchResult.successCount;
                result.failureCount += batchResult.failureCount;
                result.totalBytes += batchResult.totalBytes;
            }
        });
    }

    /**
     * Send messages across all producers in parallel and aggregate results.
     */
    public IdempotentKafkaProducer.PerformanceResult sendDistributed(int totalMessages, int messageSize) {
        int messagesPerProducer = totalMessages / poolSize;
        int remainingMessages = totalMessages % poolSize;

        IdempotentKafkaProducer.PerformanceResult aggregatedResult =
            new IdempotentKafkaProducer.PerformanceResult();
        aggregatedResult.successCount = 0;
        aggregatedResult.failureCount = 0;
        aggregatedResult.totalBytes = 0;

        long startTime = System.currentTimeMillis();

        // Submit tasks to all producers
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            final int producerIndex = i;
            int messagesForThisProducer = messagesPerProducer + (i < remainingMessages ? 1 : 0);

            Thread thread = new Thread(() -> {
                IdempotentKafkaProducer producer = producers.get(producerIndex);
                IdempotentKafkaProducer.PerformanceResult result =
                    producer.sendBatch(messagesForThisProducer, messageSize, 1000, 10);

                synchronized (aggregatedResult) {
                    aggregatedResult.successCount += result.successCount;
                    aggregatedResult.failureCount += result.failureCount;
                    aggregatedResult.totalBytes += result.totalBytes;
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        aggregatedResult.durationMs = duration;
        aggregatedResult.throughput = aggregatedResult.successCount / (double) Math.max(duration / 1000, 1);
        aggregatedResult.mbPerSec = (aggregatedResult.totalBytes / (1024.0 * 1024.0)) / Math.max(duration / 1000.0, 1);
        aggregatedResult.avgLatencyMs = duration / (double) Math.max(aggregatedResult.successCount, 1);

        log.info("=== ProducerPool Performance Results ===");
        log.info("Pool Size: {}", poolSize);
        log.info("Total Messages: {}", aggregatedResult.successCount + aggregatedResult.failureCount);
        log.info("Successful: {}, Failed: {}", aggregatedResult.successCount, aggregatedResult.failureCount);
        log.info("Duration: {} ms, Throughput: {} msg/sec",
                duration, String.format("%.2f", aggregatedResult.throughput));

        return aggregatedResult;
    }

    /**
     * Send messages synchronously across all producers (blocking).
     */
    public IdempotentKafkaProducer.PerformanceResult sendDistributedSync(int totalMessages, int messageSize) {
        int messagesPerProducer = totalMessages / poolSize;
        int remainingMessages = totalMessages % poolSize;

        IdempotentKafkaProducer.PerformanceResult aggregatedResult =
            new IdempotentKafkaProducer.PerformanceResult();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < poolSize; i++) {
            int messagesForThisProducer = messagesPerProducer + (i < remainingMessages ? 1 : 0);
            IdempotentKafkaProducer.PerformanceResult result =
                producers.get(i).sendBatchSync(messagesForThisProducer, messageSize);

            aggregatedResult.successCount += result.successCount;
            aggregatedResult.failureCount += result.failureCount;
            aggregatedResult.totalBytes += result.totalBytes;
        }

        long duration = System.currentTimeMillis() - startTime;
        aggregatedResult.durationMs = duration;
        aggregatedResult.throughput = aggregatedResult.successCount / (double) Math.max(duration / 1000, 1);
        aggregatedResult.mbPerSec = (aggregatedResult.totalBytes / (1024.0 * 1024.0)) / Math.max(duration / 1000.0, 1);
        aggregatedResult.avgLatencyMs = duration / (double) Math.max(aggregatedResult.successCount, 1);

        log.info("=== ProducerPool Sync Performance Results ===");
        log.info("Pool Size: {}", poolSize);
        log.info("Total Messages: {}", aggregatedResult.successCount + aggregatedResult.failureCount);
        log.info("Throughput: {} msg/sec", String.format("%.2f", aggregatedResult.throughput));

        return aggregatedResult;
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
        for (IdempotentKafkaProducer producer : producers) {
            producer.close();
        }
        log.info("ProducerPool closed");
    }

    public int getPoolSize() {
        return poolSize;
    }
}
