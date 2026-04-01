package com.shinyi.eventbus.demo.config;

import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "eventbus.kafka")
public class KafkaConfig {

    private String bootstrapServers = "localhost:9092";
    private String topic = "demo-topic";
    private String groupId = "demo-group";

    // Producer settings - optimized for 80亿/天 high throughput
    private String acks = "1";
    private int batchSize = 131072;           // 128KB batch
    private int lingerMs = 20;                // 20ms linger
    private int bufferMemory = 134217728;     // 128MB buffer
    private String compressionType = "snappy";
    private int maxInFlightRequestsPerConnection = 5;

    // Consumer settings - optimized for 80亿/天 high throughput
    private int maxPollRecords = 10000;        // 10K records per poll
    private int fetchMinBytes = 1048576;      // 1MB min fetch
    private int fetchMaxWaitMs = 500;         // 500ms max wait
    private int maxPartitionFetchBytes = 52428800; // 50MB per partition
    private int sessionTimeoutMs = 45000;
    private int maxPollIntervalMs = 600000;  // 10min processing window
    private int receiveBufferBytes = 131072;
    private int sendBufferBytes = 131072;

    // EOS settings
    private boolean enableIdempotence = false;
    private boolean enableManualCommit = false;
    private int commitBatchSize = 500;

    public KafkaConnectConfig toConnectConfig() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers(bootstrapServers);
        config.setTopic(topic);
        config.setGroupId(groupId);
        config.setAcks(acks);
        config.setRetries(enableIdempotence ? Integer.MAX_VALUE : 3);
        config.setBatchSize(batchSize);
        config.setLingerMs(lingerMs);
        config.setBufferMemory(bufferMemory);
        config.setCompressionType(compressionType);
        config.setMaxInFlightRequestsPerConnection(maxInFlightRequestsPerConnection);
        config.setMaxPollRecords(maxPollRecords);
        config.setFetchMinBytes(fetchMinBytes);
        config.setFetchMaxWaitMs(fetchMaxWaitMs);
        config.setMaxPartitionFetchBytes(maxPartitionFetchBytes);
        config.setSessionTimeoutMs(sessionTimeoutMs);
        config.setMaxPollIntervalMs(maxPollIntervalMs);
        config.setReceiveBufferBytes(receiveBufferBytes);
        config.setSendBufferBytes(sendBufferBytes);
        config.setEnableIdempotence(enableIdempotence);
        config.setEnableManualCommit(enableManualCommit);
        config.setCommitBatchSize(commitBatchSize);
        return config;
    }
}
