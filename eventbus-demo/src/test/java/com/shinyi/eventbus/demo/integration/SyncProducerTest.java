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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SyncProducerTest extends BaseIntegrationTest {

    private RegistryManagerHolder holder;
    private EventListenerRegistryManager registryManager;

    @BeforeEach
    void setUp() {
        KafkaConnectConfig config = createTestConfig(false);
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
    @DisplayName("Sync publish and consume - basic test")
    void testSyncPublishConsume() throws Exception {
        log.info("=== Testing SYNC publish/consume ===");

        KafkaConnectConfig config = createTestConfig(false);

        // Create consumer first
        org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> consumer =
                createKafkaConsumer(config);

        // Send messages synchronously
        int count = 20;
        for (int i = 0; i < count; i++) {
            DemoEvent event = DemoEvent.of(i, "Sync message " + i);
            EventModel<DemoEvent> eventModel = EventModel.build(
                    TOPIC,
                    event,
                    String.valueOf(i),
                    false  // sync mode
            );
            registryManager.publish(EventBusType.KAFKA, eventModel);

            if (i % 5 == 0) {
                log.info("Sent {} / {} messages", i + 1, count);
            }
        }
        log.info("All {} messages sent", count);

        // Consume and verify
        Set<String> receivedKeys = new HashSet<>();
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

        log.info("Received {} / {} messages", receivedKeys.size(), count);
        assertEquals(count, receivedKeys.size(), "Should receive all sync messages");
        log.info("SYNC test PASSED");
    }
}
