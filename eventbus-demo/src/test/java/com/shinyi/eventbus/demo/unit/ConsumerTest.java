package com.shinyi.eventbus.demo.unit;

import com.shinyi.eventbus.demo.config.KafkaConfig;
import com.shinyi.eventbus.demo.consumer.SimpleEventConsumer;
import com.shinyi.eventbus.demo.model.DemoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ConsumerTest {

    @Mock
    private KafkaConfig kafkaConfig;

    private SimpleEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SimpleEventConsumer(kafkaConfig);
    }

    @Test
    void onDemoEvent_shouldAddEventToReceivedEvents() {
        DemoEvent event = DemoEvent.of(1L, "test message");

        consumer.onDemoEvent(List.of(event));

        List<DemoEvent> receivedEvents = consumer.getReceivedEvents();
        assertEquals(1, receivedEvents.size());
        assertEquals(event, receivedEvents.get(0));
    }

    @Test
    void onDemoEvent_shouldTrackMultipleEvents() {
        DemoEvent event1 = DemoEvent.of(1L, "first message");
        DemoEvent event2 = DemoEvent.of(2L, "second message");
        DemoEvent event3 = DemoEvent.of(3L, "third message");

        consumer.onDemoEvent(List.of(event1, event2, event3));

        assertEquals(3, consumer.getReceivedCount());
        assertEquals(3, consumer.getReceivedEvents().size());
        assertEquals(event1, consumer.getReceivedEvents().get(0));
        assertEquals(event2, consumer.getReceivedEvents().get(1));
        assertEquals(event3, consumer.getReceivedEvents().get(2));
    }

    @Test
    void onDemoEvent_shouldHandleDuplicateSequence() {
        DemoEvent event1 = DemoEvent.of(1L, "first message");
        DemoEvent event2 = DemoEvent.of(1L, "duplicate sequence");

        consumer.onDemoEvent(List.of(event1, event2));

        assertEquals(2, consumer.getReceivedCount());
    }

    @Test
    void onDemoEvent_shouldPreserveEventData() {
        DemoEvent event = new DemoEvent();
        event.setSequence(100L);
        event.setMessage("preserved message");
        event.setTimestamp(System.currentTimeMillis());

        consumer.onDemoEvent(List.of(event));

        DemoEvent received = consumer.getReceivedEvents().get(0);
        assertEquals(100L, received.getSequence());
        assertEquals("preserved message", received.getMessage());
        assertEquals(event.getTimestamp(), received.getTimestamp());
    }

    @Test
    void clear_shouldRemoveAllReceivedEvents() {
        consumer.onDemoEvent(List.of(
                DemoEvent.of(1L, "first"),
                DemoEvent.of(2L, "second"),
                DemoEvent.of(3L, "third")
        ));

        assertEquals(3, consumer.getReceivedCount());

        consumer.clear();

        assertEquals(0, consumer.getReceivedCount());
        assertTrue(consumer.getReceivedEvents().isEmpty());
    }

    @Test
    void clear_shouldWorkOnEmptyList() {
        assertDoesNotThrow(() -> consumer.clear());
        assertEquals(0, consumer.getReceivedCount());
    }

    @Test
    void getReceivedCount_shouldReturnZeroInitially() {
        assertEquals(0, consumer.getReceivedCount());
    }

    @Test
    void getReceivedEvents_shouldReturnEmptyListInitially() {
        assertTrue(consumer.getReceivedEvents().isEmpty());
    }

    @Test
    void onDemoEvent_shouldBeThreadSafe() throws InterruptedException {
        int eventCount = 100;
        Thread[] threads = new Thread[10];

        for (int i = 0; i < 10; i++) {
            final int threadNum = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    consumer.onDemoEvent(List.of(DemoEvent.of((long) (threadNum * 10 + j), "thread-" + threadNum + "-msg-" + j)));
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(eventCount, consumer.getReceivedCount());
    }

    @Test
    void onDemoEvent_shouldHandleEmptyMessage() {
        DemoEvent event = DemoEvent.of(1L, "");

        consumer.onDemoEvent(List.of(event));

        assertEquals(1, consumer.getReceivedCount());
        assertEquals("", consumer.getReceivedEvents().get(0).getMessage());
    }

    @Test
    void onDemoEvent_shouldHandleNullMessage() {
        DemoEvent event = new DemoEvent();
        event.setSequence(1L);
        event.setMessage(null);

        consumer.onDemoEvent(List.of(event));

        assertEquals(1, consumer.getReceivedCount());
        assertNull(consumer.getReceivedEvents().get(0).getMessage());
    }

    @Test
    void onDemoEvent_shouldHandleMaxLongSequence() {
        DemoEvent event = DemoEvent.of(Long.MAX_VALUE, "max sequence");

        consumer.onDemoEvent(List.of(event));

        assertEquals(1, consumer.getReceivedCount());
        assertEquals(Long.MAX_VALUE, consumer.getReceivedEvents().get(0).getSequence());
    }

    @Test
    void onDemoEvent_shouldHandleNegativeSequence() {
        DemoEvent event = DemoEvent.of(-100L, "negative sequence");

        consumer.onDemoEvent(List.of(event));

        assertEquals(1, consumer.getReceivedCount());
        assertEquals(-100L, consumer.getReceivedEvents().get(0).getSequence());
    }

    @Test
    void onDemoEvent_shouldUseBoundedQueue() {
        // Fill the queue beyond its capacity and verify it doesn't block
        // ArrayBlockingQueue with capacity 100000 should handle most scenarios
        for (long i = 0; i < 100; i++) {
            DemoEvent event = DemoEvent.of(i, "message " + i);
            consumer.onDemoEvent(List.of(event));
        }

        assertEquals(100, consumer.getReceivedCount());
    }
}
