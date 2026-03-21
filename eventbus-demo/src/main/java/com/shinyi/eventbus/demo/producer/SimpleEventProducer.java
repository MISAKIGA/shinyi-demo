package com.shinyi.eventbus.demo.producer;

import com.shinyi.eventbus.EventBusType;
import com.shinyi.eventbus.EventModel;
import com.shinyi.eventbus.demo.model.DemoEvent;
import com.shinyi.eventbus.registry.KafkaMqEventListenerRegistry;
import com.shinyi.eventbus.support.EventListenerRegistryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class SimpleEventProducer {

    private final EventListenerRegistryManager registryManager;
    private final String topic;
    private final ExecutorService executorService;

    public SimpleEventProducer(EventListenerRegistryManager registryManager,
                               com.shinyi.eventbus.demo.config.KafkaConfig kafkaConfig) {
        this.registryManager = registryManager;
        this.topic = kafkaConfig.getTopic();
        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * Sync publish - blocks until acknowledgment
     */
    public void publishSync(DemoEvent event) {
        EventModel<DemoEvent> eventModel = EventModel.build(
                topic,
                event,
                String.valueOf(event.getSequence()),
                false,  // sync mode
                "JSON",
                null
        );
        registryManager.publish(EventBusType.KAFKA, eventModel);
        log.info("[SYNC] Published event #{}", event.getSequence());
    }

    /**
     * Async publish - returns immediately
     */
    public CompletableFuture<Void> publishAsync(DemoEvent event) {
        return CompletableFuture.runAsync(() -> {
            EventModel<DemoEvent> eventModel = EventModel.build(
                    topic,
                    event,
                    String.valueOf(event.getSequence()),
                    true,   // async mode
                    "JSON",
                    null
            );
            registryManager.publish(EventBusType.KAFKA, eventModel);
            log.info("[ASYNC] Published event #{}", event.getSequence());
        }, executorService);
    }

    /**
     * Simple fire-and-forget publish
     */
    public void publish(DemoEvent event) {
        EventModel<DemoEvent> eventModel = EventModel.build(
                topic,
                event,
                String.valueOf(event.getSequence()),
                false,
                "JSON",
                null
        );
        registryManager.publish(EventBusType.KAFKA, eventModel);
    }
}
