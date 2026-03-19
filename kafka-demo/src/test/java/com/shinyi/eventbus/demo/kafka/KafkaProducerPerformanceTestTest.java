package com.shinyi.eventbus.demo.kafka;

import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KafkaProducerPerformanceTest
 */
public class KafkaProducerPerformanceTestTest {

    @Test
    public void testBuilderDefaultValues() {
        KafkaProducerPerformanceTest test = KafkaProducerPerformanceTest.builder()
                .bootstrapServers("localhost:9092")
                .topic("test-topic")
                .build();

        assertNotNull(test);
    }

    @Test
    public void testBuilderWithAllParameters() {
        KafkaProducerPerformanceTest test = KafkaProducerPerformanceTest.builder()
                .bootstrapServers("10.10.10.30:9092")
                .topic("my-topic")
                .acks("all")
                .retries(5)
                .batchSize(32768)
                .lingerMs(10)
                .bufferMemory(67108864)
                .messageSize(2048)
                .messageCount(5000)
                .build();

        assertNotNull(test);
    }

    @Test
    public void testBuilderWithSecuritySettings() {
        KafkaProducerPerformanceTest test = KafkaProducerPerformanceTest.builder()
                .bootstrapServers("10.10.10.30:9092")
                .topic("secure-topic")
                .securityProtocol("SASL_PLAINTEXT")
                .saslMechanism("PLAIN")
                .username("admin")
                .password("secret")
                .build();

        assertNotNull(test);
    }

    @Test
    public void testBuilderWithKerberosSettings() {
        KafkaProducerPerformanceTest test = KafkaProducerPerformanceTest.builder()
                .bootstrapServers("10.10.10.30:9092")
                .topic("kerberos-topic")
                .securityProtocol("SASL_PLAINTEXT")
                .saslMechanism("GSSAPI")
                .kerberosServiceName("kafka")
                .kerberosPrincipal("kafka/kafka.example.com@EXAMPLE.COM")
                .kerberosKeytab("/etc/kafka/kafka.keytab")
                .build();

        assertNotNull(test);
    }
}
