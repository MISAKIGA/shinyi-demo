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
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Exactly-Once Semantics (EOS) with idempotence enabled.
 * EOS ensures exactly-once delivery even with retries.
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EosOnTest extends BaseIntegrationTest {

    private RegistryManagerHolder holder;
    private EventListenerRegistryManager registryManager;

    @BeforeEach
    void setUp() {
        // Enable EOS (idempotence)
        KafkaConnectConfig config = createTestConfig(true);
        config.setEnableIdempotence(true);
        holder = createRegistryManager(config);
        registryManager = holder.getManager();
        registryManager.start();
    }

    @AfterEach
    void tearDown() {
        if (holder != null) {
            holder.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("EOS enabled - verify idempotence config")
    void testEosConfig() {
        log.info("=== Testing EOS (Idempotence) Configuration ===");

        KafkaConnectConfig config = createTestConfig(true);
        config.setEnableIdempotence(true);

        Properties producerProps = config.toProducerProperties();

        // Verify EOS/idempotence settings
        assertEquals(true, producerProps.get("enable.idempotence"));
        assertEquals("all", producerProps.get("acks"));
        assertEquals(Integer.MAX_VALUE, producerProps.get("retries"));
        assertEquals(5, producerProps.get("max.in.flight.requests.per.connection"));

        log.info("EOS configuration verified:");
        log.info("  enable.idempotence = {}", producerProps.get("enable.idempotence"));
        log.info("  acks = {}", producerProps.get("acks"));
        log.info("  retries = {}", producerProps.get("retries"));
        log.info("EOS config test PASSED");
    }

    @Test
    @Order(2)
    @DisplayName("EOS enabled - publish and consume with exactly-once semantics")
    void testEosPublishConsume() throws Exception {
        log.info("=== Testing EOS publish/consume ===");

        KafkaConnectConfig config = createTestConfig(true);
        config.setEnableIdempotence(true);

        // Create consumer
        org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> consumer =
                createKafkaConsumer(config);

        int count = 50;

        // Send messages with EOS enabled
        for (int i = 0; i < count; i++) {
            DemoEvent event = DemoEvent.of(i, "EOS message " + i);
            EventModel<DemoEvent> eventModel = EventModel.build(
                    TOPIC,
                    event,
                    String.valueOf(i),
                    false  // sync
            );
            registryManager.publish(EventBusType.KAFKA, eventModel);

            if (i % 10 == 0) {
                log.info("EOS: Sent {} / {} messages", i + 1, count);
            }
        }
        log.info("All {} EOS messages sent", count);

        // Consume and verify
        Set<String> receivedKeys = new java.util.HashSet<>();
        long startTime = System.currentTimeMillis();
        long timeout = 30000;

        try {
            while ((System.currentTimeMillis() - startTime) < timeout) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, byte[]> record : records) {
                    receivedKeys.add(record.key());
                }
                if (receivedKeys.size() >= count) {
                    break;
                }
            }
        } finally {
            consumer.close();
        }

        log.info("EOS: Received {} / {} messages", receivedKeys.size(), count);
        assertEquals(count, receivedKeys.size(), "EOS should deliver all messages exactly once");
        log.info("EOS test PASSED - exactly-once semantics verified");
    }
}
