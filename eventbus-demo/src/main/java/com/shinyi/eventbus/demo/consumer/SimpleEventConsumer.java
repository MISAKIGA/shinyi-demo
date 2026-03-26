package com.shinyi.eventbus.demo.consumer;

import com.shinyi.eventbus.anno.EventBusListener;
import com.shinyi.eventbus.demo.model.DemoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.List;

@Slf4j
@Component
public class SimpleEventConsumer {

    private static final int QUEUE_CAPACITY = 10000;
    private final BlockingQueue<DemoEvent> receivedEvents = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final String topic;

    public SimpleEventConsumer(com.shinyi.eventbus.demo.config.KafkaConfig kafkaConfig) {
        this.topic = kafkaConfig.getTopic();
    }

    @EventBusListener(topic = "${eventbus.kafka.topic}", deserializeType = com.shinyi.eventbus.SerializeType.EVENT)
    public void onDemoEvent(List<DemoEvent> events) {
        for (DemoEvent event : events) {
            log.info("[CONSUMER] Received event #{}: {}", event.getSequence(), event.getMessage());
            receivedEvents.offer(event);
        }
    }

    public List<DemoEvent> getReceivedEvents() {
        return List.copyOf(receivedEvents);
    }

    public void clear() {
        receivedEvents.clear();
    }

    public int getReceivedCount() {
        return receivedEvents.size();
    }
}
