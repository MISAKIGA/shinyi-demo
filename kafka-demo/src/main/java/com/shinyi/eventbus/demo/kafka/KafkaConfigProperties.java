package com.shinyi.eventbus.demo.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka configuration properties for the demo application
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kafka.demo")
public class KafkaConfigProperties {

    private String bootstrapServers = "localhost:9092";
    private String topic = "test-topic";
    private String groupId = "test-group";

    // Producer settings
    private int producerMessageCount = 10000;
    private int producerMessageSize = 1024;
    private String producerAcks = "1";
    private int producerRetries = 3;
    private int producerBatchSize = 65536;  // Optimized: 64KB
    private int producerLingerMs = 10;     // Optimized: 10ms
    private int producerBufferMemory = 67108864;  // Optimized: 64MB
    private String producerCompressionType = "snappy";  // New: compression

    // Consumer settings
    private int consumerMaxPollRecords = 5000;  // Optimized: 5000
    private String consumerAutoOffsetReset = "earliest";
    private boolean consumerEnableAutoCommit = true;
    private int consumerFetchMinBytes = 1024;    // New: 1KB
    private int consumerFetchMaxWaitMs = 1000;  // New: 1000ms
    private int consumerMaxPartitionFetchBytes = 10485760;  // New: 10MB

    // Pool settings
    private int producerPoolSize = 4;  // Multi-threaded producer pool size
    private int consumerPoolSize = 2;  // Multi-threaded consumer pool size

    // Idempotence settings (for exactly-once semantics)
    private boolean enableIdempotence = false;  // Enable exactly-once producer
    private int maxInFlightRequestsPerConnection = 5;  // Required for idempotence

    // Security settings
    private String securityProtocol = "PLAINTEXT";
    private String saslMechanism = "PLAIN";
    private String username;
    private String password;

    // Kerberos settings
    private String kerberosServiceName = "kafka";
    private String kerberosPrincipal;
    private String kerberosKeytab;
    private String kerberosKrb5Location;
}
