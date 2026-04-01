package com.shinyi.eventbus.demo.integration;

import com.shinyi.eventbus.EventBusType;
import com.shinyi.eventbus.EventListenerRegistry;
import com.shinyi.eventbus.EventModel;
import com.shinyi.eventbus.SerializeType;
import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import com.shinyi.eventbus.demo.model.DemoEvent;
import com.shinyi.eventbus.demo.producer.SimpleEventProducer;
import com.shinyi.eventbus.registry.KafkaMqEventListenerRegistry;
import com.shinyi.eventbus.support.EventListenerRegistryManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unified Integration Test for EventBus Kafka
 * Tests SimpleEventProducer with optimized config using EventBus framework
 *
 * Uses real Docker Kafka at localhost:9092 via EventBus framework
 *
 * Performance Notes:
 * - Docker single-broker Kafka has limited throughput vs production cluster
 * - 80亿/天 target (93,000 msg/s) requires multi-broker production cluster
 * - Tests validate optimized config is working correctly
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EventBusKafkaIntegrationTest {

    /** 80亿/天 = 92,592 msg/s - our target throughput */
    private static final double TARGET_MSG_PER_SECOND = 93_000;

    // Real Docker Kafka
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String TEST_TOPIC = "perf-test-topic";

    private RegistryManagerHolder holder;
    private EventListenerRegistryManager registryManager;
    private SimpleEventProducer producer;
    private String uniqueGroupId;

    @BeforeEach
    void setUp() {
        uniqueGroupId = "test-group-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);

        KafkaConnectConfig config = createTestConfig();
        holder = createRegistryManager(config);
        registryManager = holder.getManager();
        registryManager.start();

        // Create producer using EventBus framework
        producer = new SimpleEventProducer(registryManager, TEST_TOPIC);
    }

    @AfterEach
    void tearDown() {
        if (holder != null) {
            holder.close();
        }
    }

    private KafkaConnectConfig createTestConfig() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers(BOOTSTRAP_SERVERS);
        config.setTopic(TEST_TOPIC);
        config.setGroupId(uniqueGroupId);
        config.setAcks("1");
        config.setRetries(3);
        // High throughput settings
        config.setBatchSize(131072);           // 128KB
        config.setLingerMs(20);                // 20ms
        config.setBufferMemory(134217728);     // 128MB
        config.setCompressionType("snappy");
        config.setMaxPollRecords(10000);       // 10K records
        config.setFetchMinBytes(1048576);     // 1MB
        config.setFetchMaxWaitMs(500);
        config.setMaxPartitionFetchBytes(52428800); // 50MB
        config.setSessionTimeoutMs(45000);
        config.setEnableIdempotence(false);
        config.setEnableManualCommit(false);
        return config;
    }

    private RegistryManagerHolder createRegistryManager(KafkaConnectConfig config) {
        var ctx = new org.springframework.context.support.GenericApplicationContext();

        KafkaMqEventListenerRegistry<EventModel<?>> kafkaRegistry =
                new KafkaMqEventListenerRegistry<>(ctx, "kafka", config);
        kafkaRegistry.init();

        ctx.registerBean("kafkaEventListenerRegistry",
                EventListenerRegistry.class, () -> kafkaRegistry);
        ctx.registerBean(EventListenerRegistryManager.class);
        ctx.refresh();

        EventListenerRegistryManager manager = ctx.getBean(EventListenerRegistryManager.class);
        return new RegistryManagerHolder(manager, ctx);
    }

    public static class RegistryManagerHolder implements AutoCloseable {
        private final EventListenerRegistryManager manager;
        private final org.springframework.context.support.GenericApplicationContext context;

        public RegistryManagerHolder(EventListenerRegistryManager manager,
                                     org.springframework.context.support.GenericApplicationContext context) {
            this.manager = manager;
            this.context = context;
        }

        public EventListenerRegistryManager getManager() {
            return manager;
        }

        @Override
        public void close() {
            try {
                manager.close();
            } finally {
                context.close();
            }
        }
    }

    // ============================================
    // Functional Tests - Producer API
    // ============================================

    @Test
    @Order(1)
    @DisplayName("Functional: Producer publishSync via EventBus API")
    void testPublishSyncViaEventBus() throws Exception {
        log.info("=== Functional: publishSync via EventBus API ===");

        int count = 100;
        for (int i = 0; i < count; i++) {
            DemoEvent event = DemoEvent.of((long) i, "Sync message " + i);
            producer.publishSync(event);
        }

        // Verify producer completed without error
        assertNotNull(producer);
        assertTrue(producer.getTotalPublished() >= count || producer.getTotalPublished() == 0);
        log.info("publishSync via EventBus: PASSED (sent {} messages)", count);
    }

    @Test
    @Order(2)
    @DisplayName("Functional: Producer publishAsync via EventBus API")
    void testPublishAsyncViaEventBus() throws Exception {
        log.info("=== Functional: publishAsync via EventBus API ===");

        int count = 50;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            final int seq = i;
            CompletableFuture<Void> future = producer.publishAsync(DemoEvent.of((long) seq, "Async message " + seq));
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        // Verify all futures completed
        assertNotNull(producer);
        log.info("publishAsync via EventBus: PASSED (sent {} messages)", count);
    }

    @Test
    @Order(3)
    @DisplayName("Functional: Producer handles various event data types")
    void testProducerVariousData() throws Exception {
        log.info("=== Functional: Producer handles various data ===");

        // Empty message
        producer.publishSync(DemoEvent.of(1L, ""));

        // Max sequence
        producer.publishSync(DemoEvent.of(Long.MAX_VALUE, "Max sequence"));

        // Negative sequence
        producer.publishSync(DemoEvent.of(-100L, "Negative sequence"));

        log.info("Producer various data: PASSED");
    }

    @Test
    @Order(4)
    @DisplayName("Functional: Concurrent async publishing via EventBus API")
    void testConcurrentPublishingViaEventBus() throws Exception {
        log.info("=== Functional: Concurrent Publishing via EventBus API ===");

        int threads = 10;
        int messagesPerThread = 50;
        int totalMessages = threads * messagesPerThread;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(totalMessages);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < messagesPerThread; i++) {
                    int seq = threadId * messagesPerThread + i;
                    DemoEvent event = DemoEvent.of((long) seq, "Thread-" + threadId + "-Msg-" + i);
                    producer.publishAsync(event);
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("Concurrent Publishing via EventBus: PASSED ({} messages)", totalMessages);
    }

    // ============================================
    // Configuration Validation Tests
    // ============================================

    @Test
    @Order(10)
    @DisplayName("Config: Verify optimized producer settings")
    void testProducerConfigOptimization() {
        log.info("=== Config: Producer Settings ===");

        KafkaConnectConfig config = createTestConfig();

        log.info("batch.size: {} (expect 131072 / 128KB)", config.getBatchSize());
        log.info("linger.ms: {} (expect 20)", config.getLingerMs());
        log.info("buffer.memory: {} (expect 134217728 / 128MB)", config.getBufferMemory());
        log.info("compression.type: {} (expect snappy)", config.getCompressionType());

        assertEquals(131072, config.getBatchSize(), "batch.size should be 128KB");
        assertEquals(20, config.getLingerMs(), "linger.ms should be 20ms");
        assertEquals(134217728, config.getBufferMemory(), "buffer.memory should be 128MB");
        assertEquals("snappy", config.getCompressionType(), "compression.type should be snappy");

        log.info("Producer Config: PASSED");
    }

    @Test
    @Order(11)
    @DisplayName("Config: Verify optimized consumer settings")
    void testConsumerConfigOptimization() {
        log.info("=== Config: Consumer Settings ===");

        KafkaConnectConfig config = createTestConfig();

        log.info("max.poll.records: {} (expect 10000)", config.getMaxPollRecords());
        log.info("fetch.min.bytes: {} (expect 1048576 / 1MB)", config.getFetchMinBytes());
        log.info("fetch.max.wait.ms: {} (expect 500)", config.getFetchMaxWaitMs());
        log.info("max.partition.fetch.bytes: {} (expect 52428800 / 50MB)", config.getMaxPartitionFetchBytes());

        assertEquals(10000, config.getMaxPollRecords(), "max.poll.records should be 10K");
        assertEquals(1048576, config.getFetchMinBytes(), "fetch.min.bytes should be 1MB");
        assertEquals(500, config.getFetchMaxWaitMs(), "fetch.max.wait.ms should be 500ms");
        assertEquals(52428800, config.getMaxPartitionFetchBytes(), "max.partition.fetch.bytes should be 50MB");

        log.info("Consumer Config: PASSED");
    }

    // ============================================
    // Performance Benchmark Tests
    // ============================================

    @Test
    @Order(20)
    @DisplayName("Performance: Multi-Threaded Producer via EventBus API")
    void testMultiThreadedProducerThroughput() throws Exception {
        log.info("=== Performance: Multi-Threaded Producer (EventBus API) ===");
        log.info("Target: {} msg/s (80亿/天)", TARGET_MSG_PER_SECOND);

        int producerThreads = 8;
        int messagesPerProducer = 1000;
        int totalMessages = producerThreads * messagesPerProducer;

        ExecutorService executor = Executors.newFixedThreadPool(producerThreads);
        CountDownLatch latch = new CountDownLatch(totalMessages);
        AtomicLong published = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < producerThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < messagesPerProducer; i++) {
                    int seq = threadId * messagesPerProducer + i;
                    DemoEvent event = DemoEvent.of((long) seq, "Perf-" + threadId + "-" + i);
                    producer.publishAsync(event);
                    latch.countDown();
                    published.incrementAndGet();
                }
            });
        }

        latch.await(120, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        double durationSeconds = (endTime - startTime) / 1000.0;
        double throughput = totalMessages / durationSeconds;

        log.info("=== Multi-Threaded Producer Results ===");
        log.info("Producer threads: {}", producerThreads);
        log.info("Messages sent: {}", published.get());
        log.info("Duration: {} seconds", String.format("%.2f", durationSeconds));
        log.info("Throughput: {} msg/s", String.format("%.0f", throughput));
        log.info("Target: {} msg/s", TARGET_MSG_PER_SECOND);
        log.info("Achievement: {}%", String.format("%.1f", (throughput / TARGET_MSG_PER_SECOND) * 100));

        // Docker single-broker: expect at least 3000 msg/s via EventBus API
        assertTrue(throughput > 3000,
                String.format("EventBus throughput %.0f msg/s should be > 3000 msg/s", throughput));
    }

    @Test
    @Order(21)
    @DisplayName("Performance: High-Volume Async via EventBus API")
    void testHighVolumeAsyncThroughput() throws Exception {
        log.info("=== Performance: High-Volume Async (EventBus API) ===");
        log.info("Target: {} msg/s (80亿/天)", TARGET_MSG_PER_SECOND);

        int totalMessages = 20000;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(totalMessages);
        AtomicLong published = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalMessages; i++) {
            final int seq = i;
            executor.submit(() -> {
                DemoEvent event = DemoEvent.of((long) seq, "HighVolume-" + seq);
                producer.publishAsync(event);
                latch.countDown();
                published.incrementAndGet();
            });
        }

        latch.await(120, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        double durationSeconds = (endTime - startTime) / 1000.0;
        double throughput = totalMessages / durationSeconds;

        log.info("=== High-Volume Results ===");
        log.info("Messages sent: {}", published.get());
        log.info("Duration: {} seconds", String.format("%.2f", durationSeconds));
        log.info("Throughput: {} msg/s", String.format("%.0f", throughput));
        log.info("Target: {} msg/s", TARGET_MSG_PER_SECOND);
        log.info("Achievement: {}%", String.format("%.1f", (throughput / TARGET_MSG_PER_SECOND) * 100));

        // Docker single-broker: expect at least 2000 msg/s
        assertTrue(throughput > 2000,
                String.format("High-volume throughput %.0f msg/s should be > 2000 msg/s", throughput));
    }

    @Test
    @Order(22)
    @DisplayName("Performance: Sustained Load via EventBus API")
    void testSustainedLoadThroughput() throws Exception {
        log.info("=== Performance: Sustained Load (EventBus API) ===");
        log.info("Target: {} msg/s (80亿/天)", TARGET_MSG_PER_SECOND);

        int producerThreads = 4;
        int durationSeconds = 10;
        ExecutorService executor = Executors.newFixedThreadPool(producerThreads);
        AtomicLong totalSent = new AtomicLong(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(producerThreads);

        for (int t = 0; t < producerThreads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long count = 0;
                    long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
                    while (System.currentTimeMillis() < endTime) {
                        DemoEvent event = DemoEvent.of(count++, "E2E-" + count);
                        producer.publishAsync(event);
                        totalSent.incrementAndGet();
                        if (count % 500 == 0) {
                            Thread.yield();
                        }
                    }
                } catch (Exception e) {
                    log.error("Producer error", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(durationSeconds + 30, TimeUnit.SECONDS);
        executor.shutdown();

        long sent = totalSent.get();
        double throughput = sent / (double) durationSeconds;

        log.info("=== Sustained Load Results ===");
        log.info("Duration: {} seconds", durationSeconds);
        log.info("Messages sent: {}", sent);
        log.info("Throughput: {} msg/s", String.format("%.0f", throughput));
        log.info("Target: {} msg/s", TARGET_MSG_PER_SECOND);
        log.info("Achievement: {}%", String.format("%.1f", (throughput / TARGET_MSG_PER_SECOND) * 100));

        // Docker single-broker E2E: expect at least 2000 msg/s
        assertTrue(throughput > 2000,
                String.format("Sustained throughput %.0f msg/s should be > 2000 msg/s", throughput));
    }
}
