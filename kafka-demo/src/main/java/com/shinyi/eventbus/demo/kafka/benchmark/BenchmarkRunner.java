package com.shinyi.eventbus.demo.kafka.benchmark;

import com.shinyi.eventbus.demo.kafka.consumer.ConsumerPool;
import com.shinyi.eventbus.demo.kafka.consumer.ManualOffsetConsumer;
import com.shinyi.eventbus.demo.kafka.producer.IdempotentKafkaProducer;
import com.shinyi.eventbus.demo.kafka.producer.ProducerPool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Orchestrates all Kafka performance benchmarks:
 * - Baseline: Current single-threaded producer/consumer
 * - Optimized: Compression + batching
 * - Multi-threaded Producer: 4 threads
 * - Exactly-Once (EOS): Idempotent producer + manual commit consumer
 */
@Slf4j
public class BenchmarkRunner {

    private final String bootstrapServers;
    private final String topic;
    private final int messageCount;
    private final int messageSize;

    public BenchmarkRunner(String bootstrapServers, String topic, int messageCount, int messageSize) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.messageCount = messageCount;
        this.messageSize = messageSize;
    }

    /**
     * Run all benchmarks and return results for comparison.
     */
    public List<BenchmarkResult> runAllBenchmarks() throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();

        log.info("==============================================");
        log.info("Starting Kafka Performance Benchmark Suite");
        log.info("==============================================");
        log.info("Bootstrap Servers: {}", bootstrapServers);
        log.info("Topic: {}", topic);
        log.info("Message Count: {}", messageCount);
        log.info("Message Size: {} bytes", messageSize);
        log.info("==============================================");

        // 1. Baseline Producer Benchmark
        try {
            results.add(runBaselineProducerBenchmark());
        } catch (Exception e) {
            log.error("Baseline producer benchmark failed: {}", e.getMessage());
        }

        // 2. Optimized Producer Benchmark (compression + batching)
        try {
            results.add(runOptimizedProducerBenchmark());
        } catch (Exception e) {
            log.error("Optimized producer benchmark failed: {}", e.getMessage());
        }

        // 3. Multi-threaded Producer Benchmark
        try {
            results.add(runMultiThreadedProducerBenchmark(4));
        } catch (Exception e) {
            log.error("Multi-threaded producer benchmark failed: {}", e.getMessage());
        }

        // 4. Exactly-Once Producer Benchmark
        try {
            results.add(runExactlyOnceProducerBenchmark());
        } catch (Exception e) {
            log.error("Exactly-once producer benchmark failed: {}", e.getMessage());
        }

        // Consumer benchmarks
        Thread.sleep(2000); // Wait for messages to be available

        // 5. Baseline Consumer Benchmark
        try {
            results.add(runBaselineConsumerBenchmark());
        } catch (Exception e) {
            log.error("Baseline consumer benchmark failed: {}", e.getMessage());
        }

        // 6. Optimized Consumer Benchmark
        try {
            results.add(runOptimizedConsumerBenchmark());
        } catch (Exception e) {
            log.error("Optimized consumer benchmark failed: {}", e.getMessage());
        }

        // 7. Exactly-Once Consumer Benchmark
        try {
            results.add(runExactlyOnceConsumerBenchmark());
        } catch (Exception e) {
            log.error("Exactly-once consumer benchmark failed: {}", e.getMessage());
        }

        log.info("==============================================");
        log.info("All Benchmarks Completed");
        log.info("==============================================");

        return results;
    }

    /**
     * Run baseline producer benchmark (current single-threaded implementation).
     */
    public BenchmarkResult runBaselineProducerBenchmark() throws Exception {
        log.info("\n>>> Running BASELINE Producer Benchmark...");

        LatencyTracker latencyTracker = new LatencyTracker();
        long startTime = System.currentTimeMillis();

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("acks", "all");  // Required for idempotent producer
        props.put("retries", "3");
        props.put("batch.size", "16384");
        props.put("linger.ms", "1");
        props.put("buffer.memory", "33554432");

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, props)) {

            IdempotentKafkaProducer.PerformanceResult result =
                producer.sendBatch(messageCount, messageSize, 1000, 10);

            long duration = System.currentTimeMillis() - startTime;
            double throughput = result.successCount / (double) Math.max(duration / 1000, 1);

            return BenchmarkResult.builder()
                .benchmarkName("Baseline Producer")
                .configuration("acks=all, retries=3, batch=16KB, linger=1ms, no compression")
                .messageCount(messageCount)
                .successCount(result.successCount)
                .failureCount(result.failureCount)
                .durationMs(duration)
                .throughputMsgPerSec(throughput)
                .mbPerSec(result.mbPerSec)
                .avgLatencyMs(result.avgLatencyMs)
                .latencyStats(latencyTracker.getStats())
                .exactlyOnce(false)
                .build();
        }
    }

    /**
     * Run optimized producer benchmark (compression + larger batching).
     */
    public BenchmarkResult runOptimizedProducerBenchmark() throws Exception {
        log.info("\n>>> Running OPTIMIZED Producer Benchmark...");

        LatencyTracker latencyTracker = new LatencyTracker();
        long startTime = System.currentTimeMillis();

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("acks", "all");  // Required for idempotent producer
        props.put("retries", "3");
        props.put("batch.size", "65536");  // 64KB
        props.put("linger.ms", "10");      // 10ms
        props.put("buffer.memory", "67108864"); // 64MB
        props.put("compression.type", "snappy");

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, props)) {

            IdempotentKafkaProducer.PerformanceResult result =
                producer.sendBatch(messageCount, messageSize, 1000, 10);

            long duration = System.currentTimeMillis() - startTime;
            double throughput = result.successCount / (double) Math.max(duration / 1000, 1);

            return BenchmarkResult.builder()
                .benchmarkName("Optimized Producer")
                .configuration("acks=all, retries=3, batch=64KB, linger=10ms, snappy compression")
                .messageCount(messageCount)
                .successCount(result.successCount)
                .failureCount(result.failureCount)
                .durationMs(duration)
                .throughputMsgPerSec(throughput)
                .mbPerSec(result.mbPerSec)
                .avgLatencyMs(result.avgLatencyMs)
                .latencyStats(latencyTracker.getStats())
                .exactlyOnce(false)
                .build();
        }
    }

    /**
     * Run multi-threaded producer benchmark.
     */
    public BenchmarkResult runMultiThreadedProducerBenchmark(int poolSize) throws Exception {
        log.info("\n>>> Running MULTI-THREADED Producer Benchmark (pool size={})...", poolSize);

        long startTime = System.currentTimeMillis();

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("acks", "all");  // Required for idempotent producer
        props.put("retries", "3");
        props.put("batch.size", "65536");
        props.put("linger.ms", "10");
        props.put("buffer.memory", "67108864");
        props.put("compression.type", "snappy");

        try (ProducerPool pool = new ProducerPool(bootstrapServers, topic, poolSize, props)) {
            IdempotentKafkaProducer.PerformanceResult result =
                pool.sendDistributed(messageCount, messageSize);

            long duration = System.currentTimeMillis() - startTime;
            double throughput = result.successCount / (double) Math.max(duration / 1000, 1);

            return BenchmarkResult.builder()
                .benchmarkName("Multi-Threaded Producer")
                .configuration("pool_size=" + poolSize + ", batch=64KB, linger=10ms, snappy")
                .messageCount(messageCount)
                .successCount(result.successCount)
                .failureCount(result.failureCount)
                .durationMs(duration)
                .throughputMsgPerSec(throughput)
                .mbPerSec(result.mbPerSec)
                .avgLatencyMs(result.avgLatencyMs)
                .latencyStats(new LatencyTracker().getStats())
                .exactlyOnce(false)
                .build();
        }
    }

    /**
     * Run exactly-once producer benchmark (idempotent producer).
     */
    public BenchmarkResult runExactlyOnceProducerBenchmark() throws Exception {
        log.info("\n>>> Running EXACTLY-ONCE Producer Benchmark...");

        long startTime = System.currentTimeMillis();

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        // Idempotence settings (enable.idempotence=true is set by default in IdempotentKafkaProducer)
        props.put("acks", "all");
        props.put("retries", Integer.MAX_VALUE);
        props.put("batch.size", "65536");
        props.put("linger.ms", "10");
        props.put("buffer.memory", "67108864");
        props.put("compression.type", "snappy");

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(bootstrapServers, topic, props)) {

            IdempotentKafkaProducer.PerformanceResult result =
                producer.sendBatch(messageCount, messageSize, 1000, 10);

            long duration = System.currentTimeMillis() - startTime;
            double throughput = result.successCount / (double) Math.max(duration / 1000, 1);

            return BenchmarkResult.builder()
                .benchmarkName("Exactly-Once Producer")
                .configuration("enable.idempotence=true, acks=all, retries=MAX, batch=64KB, snappy")
                .messageCount(messageCount)
                .successCount(result.successCount)
                .failureCount(result.failureCount)
                .durationMs(duration)
                .throughputMsgPerSec(throughput)
                .mbPerSec(result.mbPerSec)
                .avgLatencyMs(result.avgLatencyMs)
                .latencyStats(new LatencyTracker().getStats())
                .exactlyOnce(true)
                .build();
        }
    }

    /**
     * Run baseline consumer benchmark (defaults).
     */
    public BenchmarkResult runBaselineConsumerBenchmark() throws Exception {
        log.info("\n>>> Running BASELINE Consumer Benchmark...");

        long startTime = System.currentTimeMillis();

        // Kafka defaults
        Properties props = new Properties();
        props.put("enable.auto.commit", "true");
        props.put("max.poll.records", "500");
        props.put("fetch.min.bytes", "1");
        props.put("fetch.max.wait.ms", "500");
        props.put("max.partition.fetch.bytes", "1048576");  // 1MB

        try (ManualOffsetConsumer consumer =
             new ManualOffsetConsumer(bootstrapServers, topic, "baseline-consumer", props, 1000)) {

            ManualOffsetConsumer.ConsumptionResult result =
                consumer.consumeSimple(messageCount, 120);

            long duration = System.currentTimeMillis() - startTime;
            double throughput = result.successCount / (double) Math.max(duration / 1000, 1);

            return BenchmarkResult.builder()
                .benchmarkName("Baseline Consumer")
                .configuration("auto.commit=true, poll.records=500, fetch.min=1B, fetch.max.wait=500ms")
                .messageCount(messageCount)
                .successCount(result.successCount)
                .failureCount(result.failureCount)
                .durationMs(duration)
                .throughputMsgPerSec(throughput)
                .mbPerSec(result.mbPerSec)
                .avgLatencyMs(result.avgLatencyMs)
                .latencyStats(new LatencyTracker().getStats())
                .exactlyOnce(false)
                .build();
        }
    }

    /**
     * Run optimized consumer benchmark (industry-optimal).
     */
    public BenchmarkResult runOptimizedConsumerBenchmark() throws Exception {
        log.info("\n>>> Running OPTIMIZED Consumer Benchmark...");

        long startTime = System.currentTimeMillis();

        // Industry-optimal high-throughput settings
        Properties props = new Properties();
        props.put("enable.auto.commit", "true");
        props.put("max.poll.records", "10000");                    // 10K per poll
        props.put("fetch.min.bytes", "524288");                     // 512KB
        props.put("fetch.max.wait.ms", "500");                     // Wait up to 500ms
        props.put("max.partition.fetch.bytes", "10485760");        // 10MB
        props.put("fetch.buffer.size", "131072");                  // 128KB socket buffer

        try (ManualOffsetConsumer consumer =
             new ManualOffsetConsumer(bootstrapServers, topic, "optimized-consumer", props, 1000)) {

            ManualOffsetConsumer.ConsumptionResult result =
                consumer.consumeSimple(messageCount, 120);

            long duration = System.currentTimeMillis() - startTime;
            double throughput = result.successCount / (double) Math.max(duration / 1000, 1);

            return BenchmarkResult.builder()
                .benchmarkName("Optimized Consumer")
                .configuration("auto.commit=true, poll.records=10000, fetch.min=512KB, fetch.max.wait=500ms")
                .messageCount(messageCount)
                .successCount(result.successCount)
                .failureCount(result.failureCount)
                .durationMs(duration)
                .throughputMsgPerSec(throughput)
                .mbPerSec(result.mbPerSec)
                .avgLatencyMs(result.avgLatencyMs)
                .latencyStats(new LatencyTracker().getStats())
                .exactlyOnce(false)
                .build();
        }
    }

    /**
     * Run exactly-once consumer benchmark (manual commit).
     */
    public BenchmarkResult runExactlyOnceConsumerBenchmark() throws Exception {
        log.info("\n>>> Running EXACTLY-ONCE Consumer Benchmark...");

        long startTime = System.currentTimeMillis();

        // Manual commit + industry-optimal settings
        Properties props = new Properties();
        props.put("enable.auto.commit", "false");
        props.put("max.poll.records", "10000");
        props.put("fetch.min.bytes", "524288");                     // 512KB
        props.put("fetch.max.wait.ms", "500");
        props.put("max.partition.fetch.bytes", "10485760");        // 10MB
        props.put("fetch.buffer.size", "131072");

        ManualOffsetConsumer.MessageProcessor processor = (key, value) -> {
            // Real processing: just validate message
            return value != null && value.length > 0;
        };

        try (ManualOffsetConsumer consumer =
             new ManualOffsetConsumer(bootstrapServers, topic, "eos-consumer", props, 1000)) {

            ManualOffsetConsumer.ConsumptionResult result =
                consumer.consumeWithManualCommit(messageCount, 120, processor);

            long duration = System.currentTimeMillis() - startTime;
            double throughput = result.successCount / (double) Math.max(duration / 1000, 1);

            return BenchmarkResult.builder()
                .benchmarkName("Exactly-Once Consumer")
                .configuration("auto.commit=false, manual commitSync, poll.records=10000, fetch.min=512KB")
                .messageCount(messageCount)
                .successCount(result.successCount)
                .failureCount(result.failureCount)
                .durationMs(duration)
                .throughputMsgPerSec(throughput)
                .mbPerSec(result.mbPerSec)
                .avgLatencyMs(result.avgLatencyMs)
                .latencyStats(new LatencyTracker().getStats())
                .exactlyOnce(true)
                .build();
        }
    }

    /**
     * Print a comparison table of all benchmark results.
     */
    public static void printComparisonTable(List<BenchmarkResult> results) {
        log.info("\n==============================================");
        log.info("BENCHMARK RESULTS COMPARISON");
        log.info("==============================================");

        String header = String.format("%-30s | %-12s | %-12s | %-10s | %-10s | %-12s | %s",
            "Benchmark", "Throughput", "Bandwidth", "Duration", "Success", "Exactly-Once", "Configuration");
        log.info(header);
        log.info("-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");

        for (BenchmarkResult result : results) {
            String row = String.format("%-30s | %-12.2f | %-12.2f | %-10d | %-10d | %-12s | %s",
                result.getBenchmarkName(),
                result.getThroughputMsgPerSec(),
                result.getMbPerSec(),
                result.getDurationMs(),
                result.getSuccessCount(),
                result.isExactlyOnce() ? "YES" : "NO",
                result.getConfiguration());
            log.info(row);
        }

        log.info("==============================================");

        // Print detailed results
        log.info("\n=== DETAILED RESULTS ===");
        for (BenchmarkResult result : results) {
            log.info("\n{}", result.toFormattedString());
        }
    }
}
