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

    // Producer settings
    private int batchSize = 16384;
    private int lingerMs = 1;
    private int bufferMemory = 33554432;
    private String compressionType = "snappy";

    // Consumer settings
    private int maxPollRecords = 500;

    // EOS settings
    private boolean enableIdempotence = false;
    private boolean enableManualCommit = false;
    private int commitBatchSize = 100;

    public KafkaConnectConfig toConnectConfig() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers(bootstrapServers);
        config.setTopic(topic);
        config.setGroupId(groupId);
        config.setAcks(enableIdempotence ? "all" : "1");
        config.setRetries(enableIdempotence ? Integer.MAX_VALUE : 3);
        config.setBatchSize(batchSize);
        config.setLingerMs(lingerMs);
        config.setBufferMemory(bufferMemory);
        config.setCompressionType(compressionType);
        config.setMaxPollRecords(maxPollRecords);
        config.setEnableIdempotence(enableIdempotence);
        config.setEnableManualCommit(enableManualCommit);
        config.setCommitBatchSize(commitBatchSize);
        return config;
    }
}
