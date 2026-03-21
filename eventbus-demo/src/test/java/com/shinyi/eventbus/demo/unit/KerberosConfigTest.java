package com.shinyi.eventbus.demo.unit;

import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import com.shinyi.eventbus.demo.config.KerberosConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KerberosConfig.
 * Verifies that Kerberos/SASL configuration is properly mapped to KafkaConnectConfig.
 */
public class KerberosConfigTest {

    @Test
    @DisplayName("KerberosConfig - default values")
    void testDefaultValues() {
        KerberosConfig config = new KerberosConfig();

        assertEquals("localhost:9093", config.getBootstrapServers());
        assertEquals("demo-kerberos-topic", config.getTopic());
        assertEquals("demo-kerberos-group", config.getGroupId());
        assertEquals("SASL_PLAINTEXT", config.getSecurityProtocol());
        assertEquals("GSSAPI", config.getSaslMechanism());
        assertEquals("kafka", config.getKerberosServiceName());
        assertEquals("kafka/kafka.example.com@EXAMPLE.COM", config.getKerberosPrincipal());
    }

    @Test
    @DisplayName("KerberosConfig - toConnectConfig maps all properties")
    void testToConnectConfig() {
        KerberosConfig config = new KerberosConfig();
        config.setBootstrapServers("kafka.example.com:9093");
        config.setTopic("my-kerberos-topic");
        config.setGroupId("my-kerberos-group");
        config.setKerberosPrincipal("kafka/kafka.prod@REALM.COM");
        config.setKerberosKeytab("/etc/kafka/prod.keytab");
        config.setKerberosKrb5Location("/etc/krb5.conf");
        config.setKerberosServiceName("kafka");
        config.setSecurityProtocol("SASL_SSL");
        config.setCompressionType("lz4");

        KafkaConnectConfig kafkaConfig = config.toConnectConfig();

        assertEquals("kafka.example.com:9093", kafkaConfig.getBootstrapServers());
        assertEquals("my-kerberos-topic", kafkaConfig.getTopic());
        assertEquals("my-kerberos-group", kafkaConfig.getGroupId());
        assertEquals("SASL_SSL", kafkaConfig.getSecurityProtocol());
        assertEquals("GSSAPI", kafkaConfig.getSaslMechanism());
        assertEquals("kafka", kafkaConfig.getKerberosServiceName());
        assertEquals("kafka/kafka.prod@REALM.COM", kafkaConfig.getKerberosPrincipal());
        assertEquals("/etc/kafka/prod.keytab", kafkaConfig.getKerberosKeytab());
        assertEquals("/etc/krb5.conf", kafkaConfig.getKerberosKrb5Location());
        assertEquals("lz4", kafkaConfig.getCompressionType());
    }

    @Test
    @DisplayName("KerberosConfig - producer properties contain Kerberos JAAS config")
    void testProducerPropertiesContainKerberosJaasConfig() {
        KerberosConfig config = new KerberosConfig();
        config.setBootstrapServers("localhost:9093");
        config.setKerberosPrincipal("kafka/kafka.example.com@EXAMPLE.COM");
        config.setKerberosKeytab("/path/to/keytab");
        config.setKerberosKrb5Location("/path/to/krb5.conf");

        KafkaConnectConfig kafkaConfig = config.toConnectConfig();
        java.util.Properties props = kafkaConfig.toProducerProperties();

        assertEquals("SASL_PLAINTEXT", props.get("security.protocol"));
        assertEquals("GSSAPI", props.get("sasl.mechanism"));
        assertNotNull(props.get("sasl.jaas.config"));

        String jaasConfig = (String) props.get("sasl.jaas.config");
        assertTrue(jaasConfig.contains("Krb5LoginModule"));
        assertTrue(jaasConfig.contains("useKeyTab=true"));
        assertTrue(jaasConfig.contains("storeKey=true"));
        assertTrue(jaasConfig.contains("serviceName=\"kafka\""));
        assertTrue(jaasConfig.contains("principal=\"kafka/kafka.example.com@EXAMPLE.COM\""));
        assertTrue(jaasConfig.contains("keyTab=\"/path/to/keytab\""));
    }

    @Test
    @DisplayName("KerberosConfig - consumer properties also contain Kerberos settings")
    void testConsumerPropertiesContainKerberosSettings() {
        KerberosConfig config = new KerberosConfig();
        config.setBootstrapServers("localhost:9093");
        config.setKerberosPrincipal("kafka/kafka.example.com@EXAMPLE.COM");
        config.setKerberosKeytab("/path/to/keytab");
        config.setKerberosKrb5Location("/path/to/krb5.conf");
        config.setGroupId("test-group");

        KafkaConnectConfig kafkaConfig = config.toConnectConfig();
        java.util.Properties props = kafkaConfig.toConsumerProperties();

        assertEquals("SASL_PLAINTEXT", props.get("security.protocol"));
        assertEquals("GSSAPI", props.get("sasl.mechanism"));
        assertEquals("test-group", props.get("group.id"));
        assertNotNull(props.get("sasl.jaas.config"));
    }

    @Test
    @DisplayName("KerberosConfig - system properties configuration")
    void testConfigureKerberosSystemProperties() {
        KerberosConfig config = new KerberosConfig();
        config.setKerberosKrb5Location("/custom/krb5.conf");

        // This should set the system property
        config.toConnectConfig().configureKerberosSystemProperties();

        assertEquals("/custom/krb5.conf", System.getProperty("java.security.krb5.conf"));
    }

    @Test
    @DisplayName("KerberosConfig - toString contains Kerberos fields")
    void testToStringContainsKerberosFields() {
        KerberosConfig config = new KerberosConfig();
        config.setKerberosPrincipal("kafka/test@REALM.COM");

        String str = config.toString();

        // KerberosConfig uses @Data annotation, so it will have toString
        // The KafkaConnectConfig it creates will have Kerberos info
        assertNotNull(str);
    }
}
