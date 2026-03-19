package com.shinyi.eventbus.demo.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.SaslConfigs;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kerberos Authentication Test for Kafka
 *
 * This test verifies that Kerberos (GSSAPI) authentication works correctly
 * with the Kafka broker.
 *
 * Usage:
 *   java -jar kafka-demo-1.0.0.jar --kafka.test.mode=kerberos-test
 */
@Slf4j
public class KafkaKerberosTest {

    private static final String SASL_KERBEROS_SERVICE_NAME = "sasl.kerberos.service.name";
    private static final String SASL_KERBEROS_PRINCIPAL = "sasl.kerberos.principal";
    private static final String SASL_KERBEROS_KEYTAB = "sasl.kerberos.keytab";
    private static final String SASL_KERBEROS_KRB5_CONF = "sasl.kerberos.krb5.conf.path";

    private final KafkaConfigProperties config;

    public KafkaKerberosTest(KafkaConfigProperties config) {
        this.config = config;
    }

    public static void main(String[] args) {
        log.info("==============================================");
        log.info("Kafka Kerberos Authentication Test");
        log.info("==============================================");

        // Create a minimal config for testing
        KafkaConfigProperties testConfig = createTestConfig();

        KafkaKerberosTest test = new KafkaKerberosTest(testConfig);

        try {
            test.runTest();
        } catch (Exception e) {
            log.error("Kerberos test FAILED", e);
            System.exit(1);
        }

        log.info("==============================================");
        log.info("Kerberos Test Completed Successfully!");
        log.info("==============================================");
    }

    private static KafkaConfigProperties createTestConfig() {
        KafkaConfigProperties cfg = new KafkaConfigProperties();
        cfg.setBootstrapServers(System.getProperty("kafka.bootstrap.servers", "10.10.10.30:9092"));
        cfg.setTopic(System.getProperty("kafka.test.topic", "kerberos-test-topic"));
        cfg.setGroupId("kerberos-test-group");
        cfg.setSecurityProtocol("SASL_PLAINTEXT");
        cfg.setSaslMechanism("GSSAPI");
        cfg.setKerberosServiceName("kafka");
        cfg.setKerberosPrincipal(System.getProperty("kafka.kerberos.principal", "kafka/kafka.example.com@EXAMPLE.COM"));
        cfg.setKerberosKeytab(System.getProperty("kafka.kerberos.keytab", "/etc/kafka/kafka.keytab"));
        cfg.setKerberosKrb5Location(System.getProperty("kafka.kerberos.krb5.location", "/etc/kafka/krb5.conf"));
        return cfg;
    }

    public void runTest() throws Exception {
        log.info("Configuration:");
        log.info("  Bootstrap Servers: {}", config.getBootstrapServers());
        log.info("  Security Protocol: {}", config.getSecurityProtocol());
        log.info("  SASL Mechanism: {}", config.getSaslMechanism());
        log.info("  Kerberos Service Name: {}", config.getKerberosServiceName());
        log.info("  Kerberos Principal: {}", config.getKerberosPrincipal());
        log.info("  Kerberos Keytab: {}", config.getKerberosKeytab());
        log.info("  Kerberos krb5.conf: {}", config.getKerberosKrb5Location());
        log.info("");

        // Set system properties for Kerberos
        configureKerberosSystemProperties();

        // Test Admin Client (describe cluster)
        testAdminClient();

        // Test Producer
        testProducer();

        // Test Consumer
        testConsumer();
    }

    private void configureKerberosSystemProperties() {
        log.info("Configuring Kerberos system properties...");

        System.setProperty("java.security.auth.login.config", "/etc/kafka/jaas.conf");
        System.setProperty("java.security.krb5.conf", config.getKerberosKrb5Location());
        System.setProperty("sun.security.krb5.debug", "true");

        log.info("  java.security.auth.login.config = {}", System.getProperty("java.security.auth.login.config"));
        log.info("  java.security.krb5.conf = {}", System.getProperty("java.security.krb5.conf"));
        log.info("  sun.security.krb5.debug = {}", System.getProperty("sun.security.krb5.debug"));
        log.info("");
    }

    private Properties createAdminProps() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, config.getSecurityProtocol());
        props.put(SaslConfigs.SASL_MECHANISM, config.getSaslMechanism());
        props.put(SASL_KERBEROS_SERVICE_NAME, config.getKerberosServiceName());
        props.put(SASL_KERBEROS_PRINCIPAL, config.getKerberosPrincipal());
        props.put(SASL_KERBEROS_KEYTAB, config.getKerberosKeytab());
        props.put(SASL_KERBEROS_KRB5_CONF, config.getKerberosKrb5Location());
        return props;
    }

    private Properties createProducerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put("security.protocol", config.getSecurityProtocol());
        props.put(SaslConfigs.SASL_MECHANISM, config.getSaslMechanism());
        props.put(SASL_KERBEROS_SERVICE_NAME, config.getKerberosServiceName());
        props.put(SASL_KERBEROS_PRINCIPAL, config.getKerberosPrincipal());
        props.put(SASL_KERBEROS_KEYTAB, config.getKerberosKeytab());
        props.put(SASL_KERBEROS_KRB5_CONF, config.getKerberosKrb5Location());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        return props;
    }

    private Properties createConsumerProps() {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getBootstrapServers());
        props.put("group.id", config.getGroupId());
        props.put("security.protocol", config.getSecurityProtocol());
        props.put("sasl.mechanism", config.getSaslMechanism());
        props.put(SASL_KERBEROS_SERVICE_NAME, config.getKerberosServiceName());
        props.put(SASL_KERBEROS_PRINCIPAL, config.getKerberosPrincipal());
        props.put(SASL_KERBEROS_KEYTAB, config.getKerberosKeytab());
        props.put(SASL_KERBEROS_KRB5_CONF, config.getKerberosKrb5Location());
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }

    private void testAdminClient() throws Exception {
        log.info("=== Testing Admin Client (Kerberos) ===");

        try (AdminClient adminClient = AdminClient.create(createAdminProps())) {
            log.info("Admin client created successfully");

            // Describe cluster
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            String clusterId = clusterResult.clusterId().get(30, TimeUnit.SECONDS);
            int nodeCount = clusterResult.controller().get(30, TimeUnit.SECONDS).id();

            log.info("Connected to Kafka cluster:");
            log.info("  Cluster ID: {}", clusterId);
            log.info("  Controller node: {}", nodeCount);
            log.info("Admin Client test PASSED!");
        }
        log.info("");
    }

    private void testProducer() throws Exception {
        log.info("=== Testing Producer (Kerberos) ===");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(createProducerProps())) {
            log.info("Producer created successfully");

            String message = "Hello from Kerberos authenticated producer! Timestamp: " + System.currentTimeMillis();
            ProducerRecord<String, String> record = new ProducerRecord<>(config.getTopic(), "kerberos-key", message);

            RecordMetadata metadata = producer.send(record).get(30, TimeUnit.SECONDS);

            log.info("Message sent successfully:");
            log.info("  Topic: {}", metadata.topic());
            log.info("  Partition: {}", metadata.partition());
            log.info("  Offset: {}", metadata.offset());
            log.info("Producer test PASSED!");
        }
        log.info("");
    }

    private void testConsumer() throws Exception {
        log.info("=== Testing Consumer (Kerberos) ===");

        org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer =
            new org.apache.kafka.clients.consumer.KafkaConsumer<>(createConsumerProps());

        log.info("Consumer created successfully");

        consumer.subscribe(java.util.Collections.singletonList(config.getTopic()));
        log.info("Subscribed to topic: {}", config.getTopic());

        // Poll for messages
        org.apache.kafka.clients.consumer.ConsumerRecords<String, String> records =
            consumer.poll(Duration.ofSeconds(10));

        log.info("Received {} messages", records.count());
        for (org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record : records) {
            log.info("Message received:");
            log.info("  Topic: {}", record.topic());
            log.info("  Partition: {}", record.partition());
            log.info("  Offset: {}", record.offset());
            log.info("  Key: {}", record.key());
            log.info("  Value: {}", record.value());
        }

        consumer.close();
        log.info("Consumer test PASSED!");
        log.info("");
    }
}
