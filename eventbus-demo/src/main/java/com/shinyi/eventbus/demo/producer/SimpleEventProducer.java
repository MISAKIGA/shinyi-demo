package com.shinyi.eventbus.demo.producer;

import com.shinyi.eventbus.EventBusType;
import com.shinyi.eventbus.EventModel;
import com.shinyi.eventbus.SerializeType;
import com.shinyi.eventbus.demo.model.DemoEvent;
import com.shinyi.eventbus.registry.KafkaMqEventListenerRegistry;
import com.shinyi.eventbus.support.EventListenerRegistryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class SimpleEventProducer {

    private final EventListenerRegistryManager registryManager;
    private final String topic;
    // High-throughput thread pool for 80亿/天
    private final ExecutorService executorService;
    private final AtomicLong totalPublished = new AtomicLong(0);

    public SimpleEventProducer(EventListenerRegistryManager registryManager,
                               @Value("${shinyi.eventbus.kafka.connect-configs.myKafka.topic}") String topic) {
        this.registryManager = registryManager;
        this.topic = topic;
        // Larger thread pool for higher throughput
        this.executorService = Executors.newFixedThreadPool(20);
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
                SerializeType.EVENT.getType(),
                null
        );
        registryManager.publish(EventBusType.KAFKA, eventModel);
        totalPublished.incrementAndGet();
    }

    /**
     * Async publish - returns immediately (recommended for high throughput)
     */
    public CompletableFuture<Void> publishAsync(DemoEvent event) {
        return CompletableFuture.runAsync(() -> {
            EventModel<DemoEvent> eventModel = EventModel.build(
                    topic,
                    event,
                    String.valueOf(event.getSequence()),
                    true,   // async mode
                    SerializeType.EVENT.getType(),
                    null
            );
            registryManager.publish(EventBusType.KAFKA, eventModel);
            totalPublished.incrementAndGet();

            // Log every 10000 events
//            if (totalPublished.get() % 10000 == 0) {
//                log.info("[PRODUCER] Throughput stats - Total published: {}", totalPublished.get());
//            }
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
                SerializeType.EVENT.getType(),
                null
        );
        registryManager.publish(EventBusType.KAFKA, eventModel);
        totalPublished.incrementAndGet();
    }

    public long getTotalPublished() {
        return totalPublished.get();
    }
}
