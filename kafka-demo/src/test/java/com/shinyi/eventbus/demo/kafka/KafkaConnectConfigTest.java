package com.shinyi.eventbus.demo.kafka;

import com.shinyi.eventbus.config.kafka.KafkaConnectConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KafkaConnectConfig using the shinyi-eventbus library
 */
public class KafkaConnectConfigTest {

    @Test
    public void testDefaultConfiguration() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers("10.10.10.30:9092");
        config.setTopic("test-topic");

        assertEquals("10.10.10.30:9092", config.getBootstrapServers());
        assertEquals("test-topic", config.getTopic());
    }

    @Test
    public void testProducerProperties() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers("10.10.10.30:9092");
        config.setTopic("test-topic");
        config.setAcks("all");
        config.setRetries(5);
        config.setBatchSize(32768);
        config.setLingerMs(10);
        config.setBufferMemory(67108864);

        Properties props = config.toProducerProperties();

        assertEquals("10.10.10.30:9092", props.get("bootstrap.servers"));
        assertEquals("all", props.get("acks"));
        assertEquals(5, props.get("retries"));
        assertEquals(32768, props.get("batch.size"));
        assertEquals(10, props.get("linger.ms"));
        assertEquals(67108864, props.get("buffer.memory"));
        assertEquals(ByteArraySerializer.class.getName(), props.get("key.serializer"));
        assertEquals(ByteArraySerializer.class.getName(), props.get("value.serializer"));
    }

    @Test
    public void testConsumerProperties() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers("10.10.10.30:9092");
        config.setTopic("test-topic");
        config.setGroupId("test-group");
        config.setClientId("test-client");
        config.setAutoOffsetReset("latest");
        config.setEnableAutoCommit(false);
        config.setMaxPollRecords(1000);

        Properties props = config.toConsumerProperties();

        assertEquals("10.10.10.30:9092", props.get("bootstrap.servers"));
        assertEquals("test-group", props.get("group.id"));
        assertEquals("test-client", props.get("client.id"));
        assertEquals("latest", props.get("auto.offset.reset"));
        assertEquals(false, props.get("enable.auto.commit"));
        assertEquals(1000, props.get("max.poll.records"));
        assertEquals(ByteArrayDeserializer.class.getName(), props.get("key.deserializer"));
        assertEquals(ByteArrayDeserializer.class.getName(), props.get("value.deserializer"));
    }

    @Test
    public void testConsumerPropertiesWithoutClientId() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers("10.10.10.30:9092");
        config.setGroupId("test-group");

        Properties props = config.toConsumerProperties();

        assertFalse(props.containsKey("client.id"));
    }

    @Test
    public void testToStringContainsAllFields() {
        KafkaConnectConfig config = new KafkaConnectConfig();
        config.setBootstrapServers("10.10.10.30:9092");
        config.setTopic("test-topic");
        config.setGroupId("test-group");
        config.setClientId("test-client");
        config.setAcks("1");
        config.setRetries(3);

        String str = config.toString();

        assertTrue(str.contains("bootstrapServers='10.10.10.30:9092'"));
        assertTrue(str.contains("topic='test-topic'"));
        assertTrue(str.contains("groupId='test-group'"));
        assertTrue(str.contains("clientId='test-client'"));
        assertTrue(str.contains("acks='1'"));
        assertTrue(str.contains("retries=3"));
    }
}
