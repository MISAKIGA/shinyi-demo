package com.shinyi.eventbus.demo.kafka.benchmark;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks latency measurements and calculates percentiles (P50, P90, P95, P99, P999).
 * Thread-safe for concurrent use.
 */
@Slf4j
public class LatencyTracker {

    private final CopyOnWriteArrayList<Long> latencies;
    private final AtomicLong count;
    private final AtomicLong sum;
    private final AtomicLong min;
    private final AtomicLong max;

    public LatencyTracker() {
        this.latencies = new CopyOnWriteArrayList<>();
        this.count = new AtomicLong(0);
        this.sum = new AtomicLong(0);
        this.min = new AtomicLong(Long.MAX_VALUE);
        this.max = new AtomicLong(Long.MIN_VALUE);
    }

    /**
     * Record a latency measurement in nanoseconds.
     */
    public void record(long latencyNanos) {
        latencies.add(latencyNanos);
        count.incrementAndGet();
        sum.addAndGet(latencyNanos);

        // Update min/max with CAS loop
        long currentMin;
        long currentMax;
        do {
            currentMin = min.get();
            if (latencyNanos >= currentMin) break;
        } while (!min.compareAndSet(currentMin, latencyNanos));

        do {
            currentMax = max.get();
            if (latencyNanos <= currentMax) break;
        } while (!max.compareAndSet(currentMax, latencyNanos));
    }

    /**
     * Record a latency measurement in milliseconds.
     */
    public void recordMs(long latencyMs) {
        record(latencyMs * 1_000_000); // Convert to nanoseconds
    }

    /**
     * Get the count of recorded latencies.
     */
    public long getCount() {
        return count.get();
    }

    /**
     * Calculate and return latency statistics.
     */
    public LatencyStats getStats() {
        if (latencies.isEmpty()) {
            return new LatencyStats();
        }

        long[] sorted = latencies.stream()
            .mapToLong(Long::longValue)
            .sorted()
            .toArray();

        int size = sorted.length;

        LatencyStats stats = new LatencyStats();
        stats.count = size;
        stats.sumNanos = sum.get();
        stats.minNanos = min.get();
        stats.maxNanos = max.get();
        stats.meanNanos = sum.get() / (double) size;
        stats.p50Nanos = percentile(sorted, 0.50);
        stats.p90Nanos = percentile(sorted, 0.90);
        stats.p95Nanos = percentile(sorted, 0.95);
        stats.p99Nanos = percentile(sorted, 0.99);
        stats.p999Nanos = percentile(sorted, 0.999);

        return stats;
    }

    private long percentile(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        int index = (int) Math.ceil(p * sorted.length) - 1;
        index = Math.max(0, Math.min(index, sorted.length - 1));
        return sorted[index];
    }

    /**
     * Reset all tracked latencies.
     */
    public void reset() {
        latencies.clear();
        count.set(0);
        sum.set(0);
        min.set(Long.MAX_VALUE);
        max.set(Long.MIN_VALUE);
    }

    /**
     * Get summary statistics for additional metrics.
     */
    public LongSummaryStatistics getSummary() {
        return latencies.stream()
            .mapToLong(Long::longValue)
            .summaryStatistics();
    }

    @Override
    public String toString() {
        LatencyStats stats = getStats();
        if (stats.count == 0) {
            return "LatencyTracker{no data}";
        }
        return String.format(
            "LatencyTracker{count=%d, min=%.2fms, mean=%.2fms, max=%.2fms, p50=%.2fms, p90=%.2fms, p95=%.2fms, p99=%.2fms, p999=%.2fms}",
            stats.count,
            stats.minNanos / 1_000_000.0,
            stats.meanNanos / 1_000_000.0,
            stats.maxNanos / 1_000_000.0,
            stats.p50Nanos / 1_000_000.0,
            stats.p90Nanos / 1_000_000.0,
            stats.p95Nanos / 1_000_000.0,
            stats.p99Nanos / 1_000_000.0,
            stats.p999Nanos / 1_000_000.0
        );
    }

    public static class LatencyStats {
        public long count;
        public long sumNanos;
        public long minNanos;
        public long maxNanos;
        public double meanNanos;
        public long p50Nanos;
        public long p90Nanos;
        public long p95Nanos;
        public long p99Nanos;
        public long p999Nanos;

        public double getMinMs() { return minNanos / 1_000_000.0; }
        public double getMaxMs() { return maxNanos / 1_000_000.0; }
        public double getMeanMs() { return meanNanos / 1_000_000.0; }
        public double getP50Ms() { return p50Nanos / 1_000_000.0; }
        public double getP90Ms() { return p90Nanos / 1_000_000.0; }
        public double getP95Ms() { return p95Nanos / 1_000_000.0; }
        public double getP99Ms() { return p99Nanos / 1_000_000.0; }
        public double getP999Ms() { return p999Nanos / 1_000_000.0; }

        @Override
        public String toString() {
            return String.format(
                "LatencyStats{count=%d, min=%.2fms, mean=%.2fms, max=%.2fms, p50=%.2fms, p90=%.2fms, p95=%.2fms, p99=%.2fms, p999=%.2fms}",
                count, getMinMs(), getMeanMs(), getMaxMs(), getP50Ms(), getP90Ms(), getP95Ms(), getP99Ms(), getP999Ms()
            );
        }
    }
}
