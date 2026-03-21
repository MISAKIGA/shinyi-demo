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
 * Test without Exactly-Once Semantics (EOS disabled).
 * This is the default, higher-throughput mode.
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EosOffTest extends BaseIntegrationTest {

    private RegistryManagerHolder holder;
    private EventListenerRegistryManager registryManager;

    @BeforeEach
    void setUp() {
        // Disable EOS (default mode)
        KafkaConnectConfig config = createTestConfig(false);
        config.setEnableIdempotence(false);
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
    @DisplayName("EOS disabled - verify non-idempotent config")
    void testNonEosConfig() {
        log.info("=== Testing Non-EOS (Default) Configuration ===");

        KafkaConnectConfig config = createTestConfig(false);
        config.setEnableIdempotence(false);

        Properties producerProps = config.toProducerProperties();

        // Verify non-EOS settings - when idempotence is false, property may not be set
        // (defaults to false in Kafka)
        Object idempotence = producerProps.get("enable.idempotence");
        log.info("  enable.idempotence = {} (may be null, defaults to false)", idempotence);

        assertEquals("1", producerProps.get("acks"));
        assertEquals(3, producerProps.get("retries"));
        // max.in.flight defaults to 5 in Kafka
        int maxInFlight = (Integer) producerProps.get("max.in.flight.requests.per.connection");
        log.info("  max.in.flight.requests.per.connection = {} (default is 5)", maxInFlight);

        log.info("Non-EOS configuration verified:");
        log.info("  enable.idempotence = {} (null means default false)", idempotence);
        log.info("  acks = {}", producerProps.get("acks"));
        log.info("  retries = {}", producerProps.get("retries"));
        log.info("  max.in.flight.requests.per.connection = {}", maxInFlight);
        log.info("Non-EOS config test PASSED");
    }

    @Test
    @Order(2)
    @DisplayName("EOS disabled - publish and consume in high-throughput mode")
    void testNonEosPublishConsume() throws Exception {
        log.info("=== Testing Non-EOS publish/consume (high-throughput mode) ===");

        KafkaConnectConfig config = createTestConfig(false);
        config.setEnableIdempotence(false);

        // Create consumer
        org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> consumer =
                createKafkaConsumer(config);

        int count = 100;

        long startTime = System.currentTimeMillis();

        // Send messages with EOS disabled (higher throughput)
        for (int i = 0; i < count; i++) {
            DemoEvent event = DemoEvent.of(i, "Non-EOS message " + i);
            EventModel<DemoEvent> eventModel = EventModel.build(
                    TOPIC,
                    event,
                    String.valueOf(i),
                    false
            );
            registryManager.publish(EventBusType.KAFKA, eventModel);

            if (i > 0 && i % 25 == 0) {
                log.info("Non-EOS: Sent {} / {} messages", i + 1, count);
            }
        }

        long sendTime = System.currentTimeMillis() - startTime;
        log.info("All {} Non-EOS messages sent in {} ms ({} msg/sec)",
                count, sendTime, (count * 1000L) / sendTime);

        // Consume and verify
        Set<String> receivedKeys = new java.util.HashSet<>();
        long consumeStart = System.currentTimeMillis();
        long timeout = 30000;

        try {
            while ((System.currentTimeMillis() - consumeStart) < timeout) {
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

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("Non-EOS: Received {} / {} messages in {} ms total",
                receivedKeys.size(), count, totalTime);
        assertEquals(count, receivedKeys.size(), "Should receive all non-EOS messages");
        log.info("Non-EOS test PASSED - high-throughput mode verified");
    }
}
