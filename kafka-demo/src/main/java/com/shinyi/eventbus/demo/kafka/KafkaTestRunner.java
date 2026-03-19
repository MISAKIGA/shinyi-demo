package com.shinyi.eventbus.demo.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Command-line runner for Kafka performance tests
 *
 * Usage:
 *   java -jar kafka-demo.jar --kafka.test.mode=producer
 *   java -jar kafka-demo.jar --kafka.test.mode=consumer
 *   java -jar kafka-demo.jar --kafka.test.mode=both
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
            default:
                log.warn("Unknown test mode: {}. Run with --kafka.test.mode=[producer|consumer|both]", testMode);
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
}
