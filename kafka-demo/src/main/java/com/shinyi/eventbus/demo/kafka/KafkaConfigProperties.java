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
    private int producerBatchSize = 16384;
    private int producerLingerMs = 1;
    private int producerBufferMemory = 33554432;

    // Consumer settings
    private int consumerMaxPollRecords = 500;
    private String consumerAutoOffsetReset = "earliest";
    private boolean consumerEnableAutoCommit = true;

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
