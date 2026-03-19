# MQ Integration Guide

This guide provides detailed information on how to use each supported Message Queue (MQ) in Shinyi EventBus.

## Supported Message Queues

Shinyi EventBus provides built-in support for the following message queues:

| Message Queue | Package | Status |
|---------------|---------|--------|
| Guava EventBus | com.shinyi.eventbus.registry.GuavaEventListenerRegistry | Built-in |
| Spring ApplicationEvent | com.shinyi.eventbus.registry.SpringEventListenerRegistry | Built-in |
| RabbitMQ | com.shinyi.eventbus.config.rabbit | Built-in |
| RocketMQ | com.shinyi.eventbus.config.rocketmq | Built-in |
| Kafka | com.shinyi.eventbus.config.kafka | Built-in |

## RabbitMQ Integration

### Configuration

```yaml
shinyi:
  eventbus:
    rabbit-mq:
      connect-configs:
        default-rabbit:
          is-default: true
          host: localhost
          port: 5672
          username: guest
          password: guest
          virtual-host: /
          # Producer settings
          publisher-confirms: true
          publisher-retry: true
          # Consumer settings
          concurrent-consumers: 3
          max-concurrent-consumers: 10
          prefetch-count: 250
```

### Usage Example

```java
@EventBusListener(
    name = "rabbitmq",
    topic = "order.created",
    group = "order-service",
    exchange = "order.exchange",
    routingKey = "order.created",
    queue = "order.queue",
    exchangeType = "topic"
)
public void onOrderCreated(EventModel<OrderDTO> event) {
    // Handle event
}
```

## RocketMQ Integration

### Configuration

```yaml
shinyi:
  eventbus:
    rocket-mq:
      connect-configs:
        default-rocket:
          is-default: true
          namesrv-addr: localhost:9876
          group-name: my-producer-group
          # Producer settings
          send-message-timeout: 3000
          compress-message-body-threshold: 4096
          max-message-size: 4194304
          # Consumer settings
          consume-thread-min: 20
          consume-thread-max: 64
          consume-message-batch-max-size: 1
```

### Usage Example

```java
@EventBusListener(
    name = "rocketmq",
    topic = "user.registered",
    group = "user-service",
    tags = "register,notification"
)
public void onUserRegistered(EventModel<UserDTO> event) {
    // Handle event
}
```

## Kafka Integration

### Configuration

```yaml
shinyi:
  eventbus:
    kafka:
      connect-configs:
        default-kafka:
          is-default: true
          bootstrap-servers: localhost:9092
          topic: my-topic
          group-id: my-consumer-group
          
          # Producer settings
          acks: 1
          retries: 3
          batch-size: 16384
          linger-ms: 1
          buffer-memory: 33554432
          max-in-flight-requests-per-connection: 5
          
          # Consumer settings
          auto-offset-reset: earliest
          enable-auto-commit: true
          auto-commit-interval-ms: 5000
          session-timeout-ms: 30000
          max-poll-records: 500
          max-poll-interval-ms: 300000
          
          # Advanced settings
          client-id: my-client-id
          receive-buffer-bytes: 65536
          send-buffer-bytes: 131072
```

### Usage Example

```java
@EventBusListener(
    name = "kafka",
    topic = "user.login",
    group = "login-service"
)
public void onUserLogin(EventModel<LoginEvent> event) {
    System.out.println("Received login event: " + event.getEntity());
}
```

### Kafka Event Publishing

```java
@Service
public class EventPublisher {

    @Autowired
    private EventListenerRegistryManager eventRegistryManager;

    public void publishLoginEvent(LoginEvent loginEvent) {
        // Synchronous publish
        eventRegistryManager.publish("kafka", 
            EventModel.build("user.login", loginEvent, false));
        
        // Asynchronous publish
        eventRegistryManager.publish("kafka", 
            EventModel.build("user.login", loginEvent, true));
    }
}
```

### Serialization Options

Kafka supports multiple serialization types through the `@EventBusListener` annotation:

```java
// Default JSON serialization
@EventBusListener(name = "kafka", topic = "event.json", serializeType = "DEFAULT")
public void onJsonEvent(EventModel<MyEvent> event) { }

// Raw message (no deserialization)
@EventBusListener(name = "kafka", topic = "event.raw", serializeType = "MSG")
public void onRawEvent(EventModel<?> event) { 
    byte[] rawData = event.getRawData();
}
```

## Local Event Bus (Guava/Spring)

### Guava EventBus Configuration

```yaml
shinyi:
  eventbus:
    # Local event bus uses thread pool configuration
    thread-pool-core-size: 4
    thread-pool-max-size: 8
    max-queue-size: 10000
```

### Usage Example

```java
@EventBusListener(name = "guava", topic = "user.created")
public void onUserCreated(EventModel<String> event) {
    System.out.println("Local event: " + event.getEntity());
}
```

### Spring ApplicationEvent Usage

```java
@EventBusListener(name = "spring", topic = "spring.event")
public void onSpringEvent(EventModel<Object> event) {
    // Handle Spring application event
}
```

## Extending with Custom MQ Providers

Shinyi EventBus uses a registry-based system, making it easy to add support for other messaging systems.

### Step 1: Implement EventListenerRegistry

Create a new class implementing `EventListenerRegistry<EventModel<?>>`.

```java
public class CustomMqEventListenerRegistry implements EventListenerRegistry<EventModel<?>> {

    private final String name;
    private final CustomMqConfig config;

    public CustomMqEventListenerRegistry(String name, CustomMqConfig config) {
        this.name = name;
        this.config = config;
    }

    @Override
    public EventBusType getEventBusType() {
        return EventBusType.CUSTOM_MQ;
    }

    @Override
    public void initRegistryEventListener(List<EventListener<EventModel<?>>> listeners) {
        // Initialize consumer based on listeners
    }

    @Override
    public void publish(EventModel<?> event) {
        // Publish to custom MQ
    }

    @Override
    public void close() {
        // Clean up resources
    }
}
```

### Step 2: Create Configuration Properties

```java
@ConfigurationProperties(prefix = "shinyi.eventbus.custom-mq")
public class CustomMqConfig {
    private Map<String, CustomMqConnectConfig> connectConfigs;
}
```

### Step 3: Create Auto-Configuration

```java
@Configuration
@ConditionalOnBean(CustomMqConfig.class)
public class CustomMqAutoConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    public void registerBeanDefinitions() {
        // Register CustomMqEventListenerRegistry beans
    }
}
```
