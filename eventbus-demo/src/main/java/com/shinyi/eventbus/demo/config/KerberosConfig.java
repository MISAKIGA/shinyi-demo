package com.shinyi.eventbus.demo.config;

import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka Kerberos Configuration for SASL/GSSAPI authentication.
 *
 * Required properties for Kerberos:
 * - securityProtocol: SASL_PLAINTEXT or SASL_SSL
 * - saslMechanism: GSSAPI
 * - kerberosServiceName: kafka
 * - kerberosPrincipal: kafka/kafka.example.com@EXAMPLE.COM
 * - kerberosKeytab: /path/to/kafka.keytab
 * - kerberosKrb5Location: /path/to/krb5.conf
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "eventbus.kafka.kerberos")
public class KerberosConfig {

    private String bootstrapServers = "localhost:9093";
    private String topic = "demo-kerberos-topic";
    private String groupId = "demo-kerberos-group";

    // Kerberos settings
    private String securityProtocol = "SASL_PLAINTEXT";
    private String saslMechanism = "GSSAPI";
    private String kerberosServiceName = "kafka";
    private String kerberosPrincipal = "kafka/kafka.example.com@EXAMPLE.COM";
    private String kerberosKeytab;
    private String kerberosKrb5Location;

    // Producer settings
    private int batchSize = 16384;
    private int lingerMs = 1;
    private int bufferMemory = 33554432;
    private String compressionType = "snappy";

    // Consumer settings
    private int maxPollRecords = 500;

    public KafkaConnectConfig toConnectConfig() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers(bootstrapServers);
        config.setTopic(topic);
        config.setGroupId(groupId);
        config.setAcks("1");
        config.setRetries(3);
        config.setBatchSize(batchSize);
        config.setLingerMs(lingerMs);
        config.setBufferMemory(bufferMemory);
        config.setCompressionType(compressionType);
        config.setMaxPollRecords(maxPollRecords);

        // Kerberos / SASL settings
        config.setSecurityProtocol(securityProtocol);
        config.setSaslMechanism(saslMechanism);
        config.setKerberosServiceName(kerberosServiceName);
        config.setKerberosPrincipal(kerberosPrincipal);
        config.setKerberosKeytab(kerberosKeytab);
        config.setKerberosKrb5Location(kerberosKrb5Location);

        return config;
    }
}
