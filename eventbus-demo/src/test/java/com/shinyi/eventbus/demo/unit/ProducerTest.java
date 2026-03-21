package com.shinyi.eventbus.demo.unit;

import com.shinyi.eventbus.EventBusType;
import com.shinyi.eventbus.EventModel;
import com.shinyi.eventbus.demo.config.KafkaConfig;
import com.shinyi.eventbus.demo.model.DemoEvent;
import com.shinyi.eventbus.demo.producer.SimpleEventProducer;
import com.shinyi.eventbus.support.EventListenerRegistryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProducerTest {

    @Mock
    private EventListenerRegistryManager registryManager;

    @Mock
    private KafkaConfig kafkaConfig;

    private SimpleEventProducer producer;

    private static final String TEST_TOPIC = "test-topic";

    @BeforeEach
    void setUp() {
        when(kafkaConfig.getTopic()).thenReturn(TEST_TOPIC);
        producer = new SimpleEventProducer(registryManager, kafkaConfig);
    }

    @Test
    void publishSync_shouldCallRegistryPublishWithKafkaType() {
        DemoEvent event = DemoEvent.of(1L, "sync message");

        producer.publishSync(event);

        verify(registryManager, times(1)).publish(eq(EventBusType.KAFKA), any(EventModel.class));
    }

    @Test
    void publishSync_shouldInvokePublishMethod() {
        DemoEvent event = DemoEvent.of(1L, "sync message");

        producer.publishSync(event);

        verify(registryManager).publish(any(EventBusType.class), any(EventModel.class));
    }

    @Test
    void publishAsync_shouldReturnCompletableFuture() throws Exception {
        DemoEvent event = DemoEvent.of(2L, "async message");

        CompletableFuture<Void> future = producer.publishAsync(event);

        assertNotNull(future);
        future.get(5, TimeUnit.SECONDS);
        verify(registryManager, times(1)).publish(eq(EventBusType.KAFKA), any(EventModel.class));
    }

    @Test
    void publishAsync_shouldCompleteSuccessfully() throws Exception {
        DemoEvent event = DemoEvent.of(2L, "async message");

        CompletableFuture<Void> future = producer.publishAsync(event);

        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void publish_shouldCallRegistryPublish() {
        DemoEvent event = DemoEvent.of(3L, "fire-and-forget");

        producer.publish(event);

        verify(registryManager, times(1)).publish(eq(EventBusType.KAFKA), any(EventModel.class));
    }

    @Test
    void publish_shouldUseKafkaConfigTopic() {
        lenient().when(kafkaConfig.getTopic()).thenReturn("custom-topic");
        DemoEvent event = DemoEvent.of(4L, "custom topic test");

        producer.publish(event);

        verify(registryManager).publish(eq(EventBusType.KAFKA), any(EventModel.class));
    }

    @Test
    void publish_shouldThrowOnRegistryManagerException() {
        doThrow(new RuntimeException("Registry error")).when(registryManager)
                .publish(any(EventBusType.class), any(EventModel.class));

        DemoEvent event = DemoEvent.of(5L, "exception test");

        assertThrows(RuntimeException.class, () -> producer.publish(event));
    }

    @Test
    void publishSync_shouldThrowOnRegistryManagerException() {
        doThrow(new RuntimeException("Registry error")).when(registryManager)
                .publish(any(EventBusType.class), any(EventModel.class));

        DemoEvent event = DemoEvent.of(6L, "exception sync test");

        assertThrows(RuntimeException.class, () -> producer.publishSync(event));
    }

    @Test
    void publishAsync_shouldPropagateExceptionFromAsyncTask() throws Exception {
        doThrow(new RuntimeException("Async registry error")).when(registryManager)
                .publish(any(EventBusType.class), any(EventModel.class));

        DemoEvent event = DemoEvent.of(7L, "async exception test");

        CompletableFuture<Void> future = producer.publishAsync(event);

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    @Test
    void constructor_shouldInitializeWithKafkaConfigTopic() {
        verify(kafkaConfig, times(1)).getTopic();
    }

    @Test
    void publishSync_shouldHandleMultipleCalls() {
        DemoEvent event1 = DemoEvent.of(1L, "first");
        DemoEvent event2 = DemoEvent.of(2L, "second");

        producer.publishSync(event1);
        producer.publishSync(event2);

        verify(registryManager, times(2)).publish(eq(EventBusType.KAFKA), any(EventModel.class));
    }

    @Test
    void publishAsync_shouldHandleMultipleConcurrentCalls() throws Exception {
        DemoEvent event1 = DemoEvent.of(1L, "first");
        DemoEvent event2 = DemoEvent.of(2L, "second");

        CompletableFuture<Void> future1 = producer.publishAsync(event1);
        CompletableFuture<Void> future2 = producer.publishAsync(event2);

        CompletableFuture.allOf(future1, future2).get(5, TimeUnit.SECONDS);

        verify(registryManager, times(2)).publish(eq(EventBusType.KAFKA), any(EventModel.class));
    }

    @Test
    void publish_shouldAcceptEventWithNullFields() {
        DemoEvent event = new DemoEvent();

        assertDoesNotThrow(() -> producer.publish(event));
        verify(registryManager).publish(eq(EventBusType.KAFKA), any(EventModel.class));
    }
}
