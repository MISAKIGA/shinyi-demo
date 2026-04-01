package com.shinyi.eventbus.demo.consumer;

import com.shinyi.eventbus.SerializeType;
import com.shinyi.eventbus.anno.EventBusListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance raw string consumer
 * - RAW mode: no JSON parsing, direct String receive
 * - Batch processing without queue overhead
 * - Minimal logging
 */
@Slf4j
@Component
public class HighPerfRawConsumer {

    private final AtomicLong totalReceived = new AtomicLong(0);
    private final AtomicLong lastReported = new AtomicLong(0);

    @EventBusListener(
            topic = "${shinyi.eventbus.kafka.connect-configs.myKafka.topic}",
            group = "${shinyi.eventbus.kafka.connect-configs.myKafka.group-id}",
            deserializeType = SerializeType.RAW,
            entityType = String.class
    )
    public void onRawMessage(List<String> messages) {
        // Direct batch processing - no queue, no JSON parsing
        int count = messages.size();
        totalReceived.addAndGet(count);

        // Report every 100k messages
        long current = totalReceived.get();
        long last = lastReported.get();
        if (current - last >= 100_000) {
            if (lastReported.compareAndSet(last, current)) {
                log.info("[HIGH-PERF] Total received: {}", current);
            }
        }
    }

    public long getTotalReceived() {
        return totalReceived.get();
    }

    public void reset() {
        totalReceived.set(0);
        lastReported.set(0);
    }
}
