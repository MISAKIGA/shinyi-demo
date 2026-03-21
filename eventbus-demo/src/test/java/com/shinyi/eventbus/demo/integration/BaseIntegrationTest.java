package com.shinyi.eventbus.demo.integration;

import com.shinyi.eventbus.EventListenerRegistry;
import com.shinyi.eventbus.EventModel;
import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import com.shinyi.eventbus.registry.KafkaMqEventListenerRegistry;
import com.shinyi.eventbus.support.EventListenerRegistryManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public abstract class BaseIntegrationTest {

    protected static KafkaContainer kafkaContainer;
    protected static Network network;
    protected static String bootstrapServers;

    protected static final String TOPIC = "demo-test-topic";
    protected static final int MESSAGE_COUNT = 100;

    @BeforeAll
    static void startKafka() {
        network = Network.newNetwork();
        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withNetwork(network)
                .withNetworkAliases("kafka")
                .withExposedPorts(9092, 9093);

        Startables.deepStart(kafkaContainer).join();
        bootstrapServers = kafkaContainer.getBootstrapServers();
        log.info("Kafka started at: {}", bootstrapServers);
        assertNotNull(bootstrapServers, "Kafka bootstrap servers should not be null");
    }

    @AfterAll
    static void stopKafka() {
        if (kafkaContainer != null) {
            kafkaContainer.close();
        }
        if (network != null) {
            network.close();
        }
    }

    protected KafkaConnectConfig createTestConfig(boolean enableEos) {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers(bootstrapServers);
        config.setTopic(TOPIC);
        config.setGroupId("test-group-" + System.currentTimeMillis());
        config.setAcks(enableEos ? "all" : "1");
        config.setRetries(enableEos ? Integer.MAX_VALUE : 3);
        config.setBatchSize(16384);
        config.setLingerMs(1);
        config.setBufferMemory(33554432);
        config.setCompressionType("snappy");
        config.setMaxPollRecords(500);
        config.setEnableIdempotence(enableEos);
        config.setEnableManualCommit(false);
        return config;
    }

    protected RegistryManagerHolder createRegistryManager(KafkaConnectConfig config) {
        GenericApplicationContext ctx = new GenericApplicationContext();

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
        private final GenericApplicationContext context;

        public RegistryManagerHolder(EventListenerRegistryManager manager, GenericApplicationContext context) {
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

    protected org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> createKafkaConsumer(
            KafkaConnectConfig config) {
        Properties props = config.toConsumerProperties();
        org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> consumer =
                new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        consumer.subscribe(java.util.Collections.singletonList(TOPIC));
        return consumer;
    }
}
