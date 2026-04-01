package com.shinyi.eventbus.demo.consumer;

import com.shinyi.eventbus.SerializeType;
import com.shinyi.eventbus.anno.EventBusListener;
import com.shinyi.eventbus.demo.model.DemoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class SimpleEventConsumer {

    // High-capacity queue for 80亿/天 throughput
    private static final int QUEUE_CAPACITY = 100000;
    private final BlockingQueue<DemoEvent> receivedEvents = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong totalReceived = new AtomicLong(0);
    private final String topic;

    public SimpleEventConsumer(com.shinyi.eventbus.demo.config.KafkaConfig kafkaConfig) {
        this.topic = kafkaConfig.getTopic();
    }

<    @EventBusListener(topic = "${eventbus.kafka.topic}", deserializeType = SerializeType.EVENT, entityType = DemoEvent.class)
    public void onDemoEvent(List<DemoEvent> events) {
        // High-throughput processing: batch process without per-event logging
        for (DemoEvent event : events) {
            receivedEvents.offer(event);
        }
        totalReceived.addAndGet(events.size());

        // Log every 1000 events instead of every event
        if (totalReceived.get() % 1000 == 0) {
            log.info("[CONSUMER] Throughput stats - Total received: {}, Queue size: {}",
                    totalReceived.get(), receivedEvents.size());
        }
    }

    public List<DemoEvent> getReceivedEvents() {
        return List.copyOf(receivedEvents);
    }

    public void clear() {
        receivedEvents.clear();
        totalReceived.set(0);
    }

    public int getReceivedCount() {
        return receivedEvents.size();
    }

    public long getTotalReceived() {
        return totalReceived.get();
    }
}
