package com.shinyi.eventbus.demo.integration;

import com.shinyi.eventbus.EventBusType;
import com.shinyi.eventbus.EventModel;
import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import com.shinyi.eventbus.demo.model.DemoEvent;
import com.shinyi.eventbus.support.EventListenerRegistryManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base test class for Kerberos/SASL integration tests.
 *
 * This test requires the kafka-kerberos-dev environment to be running:
 * - Kafka broker with SASL_PLAINTEXT on port 9093
 * - KDC (Kerberos) server
 * - ZooKeeper
 *
 * Start the environment first:
 * cd /root/.openclaw/workspace-ceo/shinyi-demo/kafka-kerberos-dev
 * docker-compose up -d
 */
@Slf4j
public abstract class BaseKerberosIntegrationTest {

    protected static KafkaContainer kafkaContainer;
    protected static GenericContainer<?> kdcContainer;
    protected static Network network;
    protected static String bootstrapServers;

    protected static final String TOPIC = "demo-kerberos-topic";
    protected static final int MESSAGE_COUNT = 50;

    // Kerberos configuration
    protected static final String KERBEROS_PRINCIPAL = "kafka/kafka.example.com@EXAMPLE.COM";
    protected static final String KERBEROS_SERVICE_NAME = "kafka";

    @BeforeAll
    static void startKerberosKafka() {
        network = Network.newNetwork();

        // Start KDC (Kerberos) container
        kdcContainer = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withNetwork(network)
                .withNetworkAliases("kdc")
                .withExposedPorts(749)
                .withEnv("KAFKA_OPTS", "-Djava.security.auth.login.config=/etc/kafka/kafka_server_jaas.conf");

        // Note: For full Kerberos testing, use the kafka-kerberos-dev docker-compose
        // This is a simplified setup that assumes external Kerberos environment
        log.info("Kerberos integration tests require kafka-kerberos-dev environment");
        log.info("Please run: cd /root/.openclaw/workspace-ceo/shinyi-demo/kafka-kerberos-dev && docker-compose up -d");

        // For CI/CD, skip if Kerberos environment is not available
        // In a real environment, you would start the full docker-compose here
    }

    @AfterAll
    static void stopKerberosKafka() {
        if (kdcContainer != null) {
            kdcContainer.close();
        }
        if (kafkaContainer != null) {
            kafkaContainer.close();
        }
        if (network != null) {
            network.close();
        }
    }

    /**
     * Create KafkaConnectConfig for Kerberos authentication.
     * Uses the kafka-kerberos-dev configuration.
     */
    protected KafkaConnectConfig createKerberosConfig() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers(bootstrapServers != null ? bootstrapServers : "localhost:9093");
        config.setTopic(TOPIC);
        config.setGroupId("test-kerberos-group-" + System.currentTimeMillis());
        config.setSecurityProtocol("SASL_PLAINTEXT");
        config.setSaslMechanism("GSSAPI");
        config.setKerberosServiceName(KERBEROS_SERVICE_NAME);
        config.setKerberosPrincipal(KERBEROS_PRINCIPAL);
        config.setKerberosKeytab("/root/.openclaw/workspace-ceo/shinyi-demo/kafka-kerberos-dev/kafka.keytab");
        config.setKerberosKrb5Location("/root/.openclaw/workspace-ceo/shinyi-demo/kafka-kerberos-dev/krb5.conf");
        config.setAcks("1");
        config.setRetries(3);
        config.setBatchSize(16384);
        config.setLingerMs(1);
        config.setBufferMemory(33554432);
        config.setCompressionType("snappy");
        config.setMaxPollRecords(500);
        config.setEnableIdempotence(false);
        config.setEnableManualCommit(false);
        return config;
    }

    protected EventListenerRegistryManager createRegistryManager(KafkaConnectConfig config) {
        // Configure Kerberos system properties before creating clients
        config.configureKerberosSystemProperties();

        GenericApplicationContext ctx = new GenericApplicationContext();

        com.shinyi.eventbus.registry.KafkaMqEventListenerRegistry<EventModel<?>> kafkaRegistry =
                new com.shinyi.eventbus.registry.KafkaMqEventListenerRegistry<>(ctx, "kafka", config);
        kafkaRegistry.init();

        ctx.registerBean("kafkaEventListenerRegistry",
                com.shinyi.eventbus.EventListenerRegistry.class, () -> kafkaRegistry);
        ctx.registerBean(EventListenerRegistryManager.class);
        ctx.refresh();

        return ctx.getBean(EventListenerRegistryManager.class);
    }

    protected org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> createKafkaConsumer(
            KafkaConnectConfig config) {
        // Configure Kerberos system properties
        config.configureKerberosSystemProperties();

        Properties props = config.toConsumerProperties();
        org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> consumer =
                new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        consumer.subscribe(java.util.Collections.singletonList(TOPIC));
        return consumer;
    }

    /**
     * Verify that Kerberos properties are correctly set in producer config.
     */
    protected void verifyKerberosProperties(KafkaConnectConfig config) {
        Properties producerProps = config.toProducerProperties();

        assertEquals("SASL_PLAINTEXT", producerProps.get("security.protocol"));
        assertEquals("GSSAPI", producerProps.get("sasl.mechanism"));
        assertNotNull(producerProps.get("sasl.jaas.config"));
        assertTrue(((String) producerProps.get("sasl.jaas.config")).contains("Krb5LoginModule"));
        assertEquals(KERBEROS_SERVICE_NAME, producerProps.get("sasl.kerberos.service.name"));
    }
}
