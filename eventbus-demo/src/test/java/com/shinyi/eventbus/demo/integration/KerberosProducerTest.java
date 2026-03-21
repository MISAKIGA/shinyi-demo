package com.shinyi.eventbus.demo.integration;

import com.shinyi.eventbus.EventBusType;
import com.shinyi.eventbus.EventModel;
import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import com.shinyi.eventbus.demo.model.DemoEvent;
import com.shinyi.eventbus.support.EventListenerRegistryManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Kerberos/SASL authenticated Kafka producer.
 *
 * This test verifies:
 * 1. Producer can authenticate using GSSAPI/Kerberos
 * 2. Messages are successfully sent to Kafka
 * 3. Consumer can authenticate and receive messages
 *
 * Prerequisites:
 * - kafka-kerberos-dev environment must be running
 * - Keytab and krb5.conf files must be accessible
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KerberosProducerTest extends BaseKerberosIntegrationTest {

    private EventListenerRegistryManager registryManager;

    @BeforeEach
    void setUp() {
        // Skip if Kerberos environment is not available
        // In a real CI environment, you would fail here
        log.info("=== Kerberos Producer Test Setup ===");
        log.info("This test requires kafka-kerberos-dev to be running");
        log.info("If not running, test will be skipped");
    }

    @AfterEach
    void tearDown() {
        if (registryManager != null) {
            try {
                registryManager.close();
            } catch (Exception e) {
                log.warn("Error closing registry manager", e);
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Kerberos configuration - verify SASL/GSSAPI properties")
    void testKerberosConfiguration() {
        log.info("=== Testing Kerberos Configuration ===");

        KafkaConnectConfig config = createKerberosConfig();

        // Verify Kerberos properties
        assertEquals("SASL_PLAINTEXT", config.getSecurityProtocol());
        assertEquals("GSSAPI", config.getSaslMechanism());
        assertEquals("kafka", config.getKerberosServiceName());
        assertEquals(KERBEROS_PRINCIPAL, config.getKerberosPrincipal());
        assertNotNull(config.getKerberosKeytab());
        assertNotNull(config.getKerberosKrb5Location());

        // Verify producer properties contain Kerberos settings
        Properties producerProps = config.toProducerProperties();
        assertEquals("SASL_PLAINTEXT", producerProps.get("security.protocol"));
        assertEquals("GSSAPI", producerProps.get("sasl.mechanism"));
        assertNotNull(producerProps.get("sasl.jaas.config"));

        String jaasConfig = (String) producerProps.get("sasl.jaas.config");
        assertTrue(jaasConfig.contains("Krb5LoginModule"));
        assertTrue(jaasConfig.contains("useKeyTab=true"));
        assertTrue(jaasConfig.contains("principal=\"" + KERBEROS_PRINCIPAL + "\""));
        assertTrue(jaasConfig.contains("keyTab=\"" + config.getKerberosKeytab() + "\""));

        log.info("Kerberos configuration verified successfully");
        log.info("  security.protocol = {}", producerProps.get("security.protocol"));
        log.info("  sasl.mechanism = {}", producerProps.get("sasl.mechanism"));
        log.info("  sasl.jaas.config contains Krb5LoginModule: {}", jaasConfig.contains("Krb5LoginModule"));
    }

    @Test
    @Order(2)
    @DisplayName("Kerberos producer - verify system properties configuration")
    void testKerberosSystemProperties() {
        log.info("=== Testing Kerberos System Properties ===");

        KafkaConnectConfig config = createKerberosConfig();

        // Configure Kerberos system properties (this is what clients need to call)
        config.configureKerberosSystemProperties();

        // Verify krb5.conf system property is set
        assertEquals(config.getKerberosKrb5Location(), System.getProperty("java.security.krb5.conf"));

        log.info("Kerberos system properties configured:");
        log.info("  java.security.krb5.conf = {}", System.getProperty("java.security.krb5.conf"));
    }

    @Test
    @Order(3)
    @DisplayName("Kerberos consumer - verify consumer properties")
    void testKerberosConsumerProperties() {
        log.info("=== Testing Kerberos Consumer Properties ===");

        KafkaConnectConfig config = createKerberosConfig();

        Properties consumerProps = config.toConsumerProperties();

        assertEquals("SASL_PLAINTEXT", consumerProps.get("security.protocol"));
        assertEquals("GSSAPI", consumerProps.get("sasl.mechanism"));
        assertNotNull(consumerProps.get("sasl.jaas.config"));
        assertEquals("earliest", consumerProps.get("auto.offset.reset"));

        log.info("Kerberos consumer properties verified:");
        log.info("  security.protocol = {}", consumerProps.get("security.protocol"));
        log.info("  sasl.mechanism = {}", consumerProps.get("sasl.mechanism"));
        log.info("  auto.offset.reset = {}", consumerProps.get("auto.offset.reset"));
    }

    @Test
    @Order(4)
    @DisplayName("Kerberos integration - producer and consumer with authentication")
    @Disabled("Requires running kafka-kerberos-dev environment")
    void testKerberosProduceConsume() throws Exception {
        log.info("=== Testing Kerberos Producer/Consumer ===");

        KafkaConnectConfig config = createKerberosConfig();
        config.configureKerberosSystemProperties();

        registryManager = createRegistryManager(config);
        registryManager.start();

        // Create consumer
        org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> consumer =
                createKafkaConsumer(config);

        // Send messages with Kerberos auth
        int count = 20;
        for (int i = 0; i < count; i++) {
            DemoEvent event = DemoEvent.of(i, "Kerberos message " + i);
            EventModel<DemoEvent> eventModel = EventModel.build(
                    TOPIC,
                    event,
                    String.valueOf(i),
                    false  // sync mode
            );
            registryManager.publish(EventBusType.KAFKA, eventModel);

            if (i % 5 == 0) {
                log.info("Sent {} / {} messages with Kerberos auth", i + 1, count);
            }
        }
        log.info("All {} messages sent", count);

        // Consume and verify
        Set<String> receivedKeys = new HashSet<>();
        long startTime = System.currentTimeMillis();
        long timeout = 30000;

        while ((System.currentTimeMillis() - startTime) < timeout) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, byte[]> record : records) {
                receivedKeys.add(record.key());
            }
            if (receivedKeys.size() >= count) {
                break;
            }
        }

        consumer.close();

        log.info("Received {} / {} messages", receivedKeys.size(), count);
        assertEquals(count, receivedKeys.size(), "Should receive all Kerberos-authenticated messages");
        log.info("Kerberos producer/consumer test PASSED");
    }
}
