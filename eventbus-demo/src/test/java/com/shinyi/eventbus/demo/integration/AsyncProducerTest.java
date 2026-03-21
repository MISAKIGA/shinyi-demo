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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AsyncProducerTest extends BaseIntegrationTest {

    private RegistryManagerHolder holder;
    private EventListenerRegistryManager registryManager;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(10);
        KafkaConnectConfig config = createTestConfig(false);
        holder = createRegistryManager(config);
        registryManager = holder.getManager();
        registryManager.start();
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (holder != null) {
            holder.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Async publish - fire and forget")
    void testAsyncPublish() throws Exception {
        log.info("=== Testing ASYNC publish ===");

        KafkaConnectConfig config = createTestConfig(false);

        // Create consumer first
        org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> consumer =
                createKafkaConsumer(config);

        int count = 20;
        CountDownLatch latch = new CountDownLatch(count);

        // Send messages asynchronously using thread pool
        for (int i = 0; i < count; i++) {
            final int seq = i;
            DemoEvent event = DemoEvent.of(seq, "Async message " + seq);
            final EventModel<DemoEvent> eventModel = EventModel.build(
                    TOPIC,
                    event,
                    String.valueOf(seq),
                    true   // async mode flag
            );

            // Use ExecutorService instead of new Thread()
            executorService.submit(() -> {
                try {
                    registryManager.publish(EventBusType.KAFKA, eventModel);
                    log.debug("Async send completed for #{}", seq);
                } catch (Exception ex) {
                    log.error("Async send failed for #{}", seq, ex);
                } finally {
                    latch.countDown();
                }
            });

            if (i % 5 == 0) {
                log.info("Queued {} / {} messages for async send", i + 1, count);
            }
        }

        // Wait for all async sends to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        log.info("All async sends completed: {}", completed);

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
        assertTrue(receivedKeys.size() > 0, "Should receive at least some async messages");
        log.info("ASYNC test PASSED");
    }
}
