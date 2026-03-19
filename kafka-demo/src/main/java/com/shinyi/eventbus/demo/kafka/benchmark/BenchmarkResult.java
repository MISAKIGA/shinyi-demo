package com.shinyi.eventbus.demo.kafka.benchmark;

import lombok.Data;

/**
 * Data class for benchmark results.
 * Immutable - create new instances for each benchmark run.
 */
@Data
public class BenchmarkResult {
    private final String benchmarkName;
    private final String configuration;
    private final long messageCount;
    private final long successCount;
    private final long failureCount;
    private final long durationMs;
    private final double throughputMsgPerSec;
    private final double mbPerSec;
    private final double avgLatencyMs;
    private final LatencyTracker.LatencyStats latencyStats;
    private final boolean exactlyOnce;
    private final long timestamp;

    public BenchmarkResult(String benchmarkName, String configuration, long messageCount,
                          long successCount, long failureCount, long durationMs,
                          double throughputMsgPerSec, double mbPerSec, double avgLatencyMs,
                          LatencyTracker.LatencyStats latencyStats, boolean exactlyOnce) {
        this.benchmarkName = benchmarkName;
        this.configuration = configuration;
        this.messageCount = messageCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.durationMs = durationMs;
        this.throughputMsgPerSec = throughputMsgPerSec;
        this.mbPerSec = mbPerSec;
        this.avgLatencyMs = avgLatencyMs;
        this.latencyStats = latencyStats;
        this.exactlyOnce = exactlyOnce;
        this.timestamp = System.currentTimeMillis();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String benchmarkName;
        private String configuration;
        private long messageCount;
        private long successCount;
        private long failureCount;
        private long durationMs;
        private double throughputMsgPerSec;
        private double mbPerSec;
        private double avgLatencyMs;
        private LatencyTracker.LatencyStats latencyStats;
        private boolean exactlyOnce;

        public Builder benchmarkName(String benchmarkName) {
            this.benchmarkName = benchmarkName;
            return this;
        }

        public Builder configuration(String configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder messageCount(long messageCount) {
            this.messageCount = messageCount;
            return this;
        }

        public Builder successCount(long successCount) {
            this.successCount = successCount;
            return this;
        }

        public Builder failureCount(long failureCount) {
            this.failureCount = failureCount;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder throughputMsgPerSec(double throughputMsgPerSec) {
            this.throughputMsgPerSec = throughputMsgPerSec;
            return this;
        }

        public Builder mbPerSec(double mbPerSec) {
            this.mbPerSec = mbPerSec;
            return this;
        }

        public Builder avgLatencyMs(double avgLatencyMs) {
            this.avgLatencyMs = avgLatencyMs;
            return this;
        }

        public Builder latencyStats(LatencyTracker.LatencyStats latencyStats) {
            this.latencyStats = latencyStats;
            return this;
        }

        public Builder exactlyOnce(boolean exactlyOnce) {
            this.exactlyOnce = exactlyOnce;
            return this;
        }

        public BenchmarkResult build() {
            return new BenchmarkResult(
                benchmarkName, configuration, messageCount, successCount, failureCount,
                durationMs, throughputMsgPerSec, mbPerSec, avgLatencyMs, latencyStats, exactlyOnce
            );
        }
    }

    @Override
    public String toString() {
        return String.format(
            "BenchmarkResult{name='%s', msgs=%d, success=%d, failed=%d, " +
            "duration=%dms, throughput=%.2f msg/s, bandwidth=%.2f MB/s, exactlyOnce=%s}",
            benchmarkName, messageCount, successCount, failureCount,
            durationMs, throughputMsgPerSec, mbPerSec, exactlyOnce
        );
    }

    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(benchmarkName).append(" ===\n");
        sb.append("Configuration: ").append(configuration).append("\n");
        sb.append("Messages: ").append(messageCount).append("\n");
        sb.append("Success: ").append(successCount).append(", Failed: ").append(failureCount).append("\n");
        sb.append("Duration: ").append(durationMs).append(" ms\n");
        sb.append("Throughput: ").append(String.format("%.2f", throughputMsgPerSec)).append(" msg/sec\n");
        sb.append("Bandwidth: ").append(String.format("%.2f", mbPerSec)).append(" MB/sec\n");
        sb.append("Exactly-Once: ").append(exactlyOnce).append("\n");

        if (latencyStats != null && latencyStats.count > 0) {
            sb.append("Latency Stats:\n");
            sb.append("  Mean: ").append(String.format("%.2f", latencyStats.getMeanMs())).append(" ms\n");
            sb.append("  Min: ").append(String.format("%.2f", latencyStats.getMinMs())).append(" ms\n");
            sb.append("  Max: ").append(String.format("%.2f", latencyStats.getMaxMs())).append(" ms\n");
            sb.append("  P50: ").append(String.format("%.2f", latencyStats.getP50Ms())).append(" ms\n");
            sb.append("  P90: ").append(String.format("%.2f", latencyStats.getP90Ms())).append(" ms\n");
            sb.append("  P95: ").append(String.format("%.2f", latencyStats.getP95Ms())).append(" ms\n");
            sb.append("  P99: ").append(String.format("%.2f", latencyStats.getP99Ms())).append(" ms\n");
            sb.append("  P999: ").append(String.format("%.2f", latencyStats.getP999Ms())).append(" ms\n");
        }

        return sb.toString();
    }
}
