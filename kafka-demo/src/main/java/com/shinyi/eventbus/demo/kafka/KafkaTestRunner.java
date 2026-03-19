package com.shinyi.eventbus.demo.kafka;

import com.shinyi.eventbus.demo.kafka.benchmark.BenchmarkRunner;
import com.shinyi.eventbus.demo.kafka.consumer.ConsumerPool;
import com.shinyi.eventbus.demo.kafka.consumer.ManualOffsetConsumer;
import com.shinyi.eventbus.demo.kafka.producer.IdempotentKafkaProducer;
import com.shinyi.eventbus.demo.kafka.producer.ProducerPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Command-line runner for Kafka performance tests
 *
 * Usage:
 *   java -jar kafka-demo.jar --kafka.test.mode=producer
 *   java -jar kafka-demo.jar --kafka.test.mode=consumer
 *   java -jar kafka-demo.jar --kafka.test.mode=both
 *   java -jar kafka-demo.jar --kafka.test.mode=kerberos-test
 *   java -jar kafka-demo.jar --kafka.test.mode=mt-producer
 *   java -jar kafka-demo.jar --kafka.test.mode=eos
 *   java -jar kafka-demo.jar --kafka.test.mode=benchmark
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaTestRunner implements CommandLineRunner {

    private final KafkaConfigProperties config;

    @Override
    public void run(String... args) throws Exception {
        log.info("==============================================");
        log.info("Kafka Performance Test Configuration");
        log.info("==============================================");
        log.info("Bootstrap Servers: {}", config.getBootstrapServers());
        log.info("Topic: {}", config.getTopic());
        log.info("Group ID: {}", config.getGroupId());
        log.info("Security Protocol: {}", config.getSecurityProtocol());
        if ("SASL_PLAINTEXT".equals(config.getSecurityProtocol()) ||
            "SASL_SSL".equals(config.getSecurityProtocol())) {
            log.info("SASL Mechanism: {}", config.getSaslMechanism());
        }
        log.info("==============================================");

        // Set system properties for Kafka client configuration
        configureSystemProperties();

        // Determine test mode
        String testMode = getTestMode(args);
        log.info("Test Mode: {}", testMode);

        switch (testMode) {
            case "producer":
                runProducerTest();
                break;
            case "consumer":
                runConsumerTest();
                break;
            case "both":
                runProducerTest();
                Thread.sleep(2000);
                runConsumerTest();
                break;
            case "kerberos-test":
                runKerberosTest();
                break;
            case "mt-producer":
                runMultiThreadedProducerTest();
                break;
            case "eos":
                runExactlyOnceTest();
                break;
            case "benchmark":
                runBenchmarkTest();
                break;
            default:
                log.warn("Unknown test mode: {}. Run with --kafka.test.mode=[producer|consumer|both|kerberos-test|mt-producer|eos|benchmark]", testMode);
        }

        log.info("==============================================");
        log.info("Kafka Performance Test Completed!");
        log.info("==============================================");
    }

    private String getTestMode(String... args) {
        for (String arg : args) {
            if (arg.startsWith("--kafka.test.mode=")) {
                return arg.split("=", 2)[1];
            }
        }
        // Default mode
        return "both";
    }

    private void configureSystemProperties() {
        System.setProperty("kafka.security.protocol", config.getSecurityProtocol());

        if ("SASL_PLAINTEXT".equals(config.getSecurityProtocol()) ||
            "SASL_SSL".equals(config.getSecurityProtocol())) {
            System.setProperty("kafka.sasl.mechanism",
                config.getSaslMechanism() != null ? config.getSaslMechanism() : "PLAIN");
            if (config.getUsername() != null) {
                System.setProperty("kafka.sasl.username", config.getUsername());
            }
            if (config.getPassword() != null) {
                System.setProperty("kafka.sasl.password", config.getPassword());
            }
        }

        if ("GSSAPI".equals(config.getSaslMechanism())) {
            if (config.getKerberosPrincipal() != null) {
                System.setProperty("kafka.kerberos.principal", config.getKerberosPrincipal());
            }
            if (config.getKerberosKeytab() != null) {
                System.setProperty("kafka.kerberos.keytab", config.getKerberosKeytab());
            }
            if (config.getKerberosKrb5Location() != null) {
                System.setProperty("java.security.krb5.conf", config.getKerberosKrb5Location());
            }
        }
    }

    private void runProducerTest() throws Exception {
        log.info("\n>>> Running Producer Performance Test...");
        log.info("Message Count: {}", config.getProducerMessageCount());
        log.info("Message Size: {} bytes", config.getProducerMessageSize());

        KafkaProducerPerformanceTest producerTest = KafkaProducerPerformanceTest.builder()
                .bootstrapServers(config.getBootstrapServers())
                .topic(config.getTopic())
                .acks(config.getProducerAcks())
                .retries(config.getProducerRetries())
                .batchSize(config.getProducerBatchSize())
                .lingerMs(config.getProducerLingerMs())
                .bufferMemory(config.getProducerBufferMemory())
                .messageSize(config.getProducerMessageSize())
                .messageCount(config.getProducerMessageCount())
                .build();

        KafkaProducerPerformanceTest.PerformanceResult result = producerTest.runPerformanceTest(
                config.getProducerMessageCount(),
                config.getProducerMessageSize()
        );

        log.info("Producer test completed: {} msgs successful, {} failed",
                result.getSuccessCount(), result.getFailureCount());
    }

    private void runConsumerTest() throws Exception {
        log.info("\n>>> Running Consumer Performance Test...");
        log.info("Max Poll Records: {}", config.getConsumerMaxPollRecords());

        KafkaConsumerPerformanceTest consumerTest = KafkaConsumerPerformanceTest.builder()
                .bootstrapServers(config.getBootstrapServers())
                .topic(config.getTopic())
                .groupId(config.getGroupId())
                .maxPollRecords(config.getConsumerMaxPollRecords())
                .autoOffsetReset(config.getConsumerAutoOffsetReset())
                .enableAutoCommit(config.isConsumerEnableAutoCommit())
                .build();

        KafkaConsumerPerformanceTest.PerformanceResult result = consumerTest.runConsumeTest(
                config.getProducerMessageCount(),
                60
        );

        log.info("Consumer test completed: {} msgs consumed, {} failed",
                result.getSuccessCount(), result.getFailureCount());
    }

    private void runKerberosTest() throws Exception {
        log.info("\n>>> Running Kerberos Authentication Test...");
        KafkaKerberosTest kerberosTest = new KafkaKerberosTest(config);
        kerberosTest.runTest();
    }

    private void runMultiThreadedProducerTest() throws Exception {
        log.info("\n>>> Running Multi-Threaded Producer Test...");
        log.info("Pool Size: {}", config.getProducerPoolSize());
        log.info("Message Count: {}", config.getProducerMessageCount());

        Properties props = new Properties();
        props.put("bootstrap.servers", config.getBootstrapServers());
        props.put("acks", config.getProducerAcks());
        props.put("retries", config.getProducerRetries());
        props.put("batch.size", config.getProducerBatchSize());
        props.put("linger.ms", config.getProducerLingerMs());
        props.put("buffer.memory", config.getProducerBufferMemory());
        props.put("compression.type", config.getProducerCompressionType());

        try (ProducerPool pool = new ProducerPool(
                config.getBootstrapServers(),
                config.getTopic(),
                config.getProducerPoolSize(),
                props)) {

            IdempotentKafkaProducer.PerformanceResult result =
                pool.sendDistributed(config.getProducerMessageCount(), config.getProducerMessageSize());

            log.info("Multi-threaded producer test completed:");
            log.info("  {} msgs successful, {} failed", result.successCount, result.failureCount);
            log.info("  Throughput: {} msg/sec", String.format("%.2f", result.throughput));
        }
    }

    private void runExactlyOnceTest() throws Exception {
        log.info("\n>>> Running Exactly-Once Semantics Test...");
        log.info("Message Count: {}", config.getProducerMessageCount());

        // Produce with idempotent producer
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", config.getBootstrapServers());
        producerProps.put("acks", "all");
        producerProps.put("retries", Integer.MAX_VALUE);
        producerProps.put("enable.idempotence", true);
        producerProps.put("batch.size", config.getProducerBatchSize());
        producerProps.put("linger.ms", config.getProducerLingerMs());
        producerProps.put("compression.type", config.getProducerCompressionType());

        log.info("Producing {} messages with idempotent producer...", config.getProducerMessageCount());

        try (IdempotentKafkaProducer producer =
             new IdempotentKafkaProducer(config.getBootstrapServers(), config.getTopic(), producerProps)) {
            IdempotentKafkaProducer.PerformanceResult produceResult =
                producer.sendBatch(config.getProducerMessageCount(), config.getProducerMessageSize(), 1000, 10);

            log.info("Produced: {} msgs successful, {} failed",
                    produceResult.successCount, produceResult.failureCount);
        }

        Thread.sleep(2000);

        // Consume with manual commit
        Properties consumerProps = new Properties();
        consumerProps.put("enable.auto.commit", "false");
        consumerProps.put("max.poll.records", config.getConsumerMaxPollRecords());

        log.info("Consuming {} messages with manual offset commit...", config.getProducerMessageCount());

        ManualOffsetConsumer.MessageProcessor processor = (key, value) -> {
            return value != null && value.length > 0;
        };

        try (ManualOffsetConsumer consumer =
             new ManualOffsetConsumer(
                     config.getBootstrapServers(),
                     config.getTopic(),
                     config.getGroupId(),
                     consumerProps,
                     1000)) {

            ManualOffsetConsumer.ConsumptionResult consumeResult =
                consumer.consumeWithManualCommit(config.getProducerMessageCount(), 120, processor);

            log.info("Consumed: {} msgs successful, {} failed",
                    consumeResult.successCount, consumeResult.failureCount);
            log.info("Exactly-once semantics verified!");
        }
    }

    private void runBenchmarkTest() throws Exception {
        log.info("\n>>> Running Full Benchmark Suite...");
        log.info("Message Count: {}", config.getProducerMessageCount());
        log.info("Message Size: {} bytes", config.getProducerMessageSize());

        BenchmarkRunner runner = new BenchmarkRunner(
                config.getBootstrapServers(),
                config.getTopic(),
                config.getProducerMessageCount(),
                config.getProducerMessageSize()
        );

        java.util.List<com.shinyi.eventbus.demo.kafka.benchmark.BenchmarkResult> results = runner.runAllBenchmarks();
        BenchmarkRunner.printComparisonTable(results);
    }
}
