package com.shinyi.eventbus.demo;

import cn.hutool.extra.spring.SpringUtil;
import com.shinyi.eventbus.anno.EnableEventBus;
import com.shinyi.eventbus.config.kafka.KafkaAutoConfiguration;
import com.shinyi.eventbus.demo.config.KafkaConfig;
import com.shinyi.eventbus.demo.model.DemoEvent;
import com.shinyi.eventbus.demo.producer.SimpleEventProducer;
import com.shinyi.eventbus.exception.EventBusException;
import com.shinyi.eventbus.support.EventListenerRegistryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Slf4j
@EnableEventBus
@SpringBootApplication
@EnableConfigurationProperties
@Import({SpringUtil.class})
public class EventBusDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventBusDemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner runner(EventListenerRegistryManager registryManager,
                                    SimpleEventProducer producer) {
        return args -> {
            log.info("EventListenerRegistryManager isRunning: {}", registryManager.isRunning());

            // Wait for consumer to connect
            Thread.sleep(5000);

            // Publish events (don't fail the app if Kafka is unreachable)
            for (int i = 0; i < 100; i++) {
                try {
                    producer.publishSync(DemoEvent.of(i, "Hello World from Kafka!"));
                } catch (EventBusException e) {
                    log.warn("Failed to publish event #{}: {}. "
                            + "Continuing... (Kafka may be unreachable).", i, e.getMessage());
                }
            }

            log.info("Publish phase complete. App is running. Press Ctrl+C to stop.");
        };
    }
}
