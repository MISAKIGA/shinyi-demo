# EventBus Demo

A simple Spring Boot demonstration project showcasing the [shinyi-eventbus](https://github.com/misakiga/shinyi-eventbus) framework for Kafka-based event publishing and consuming.

## Project Overview

This project demonstrates how to use the shinyi-eventbus framework to implement a Kafka event bus with support for:

- Synchronous and asynchronous message publishing
- Exactly-Once Semantics (EOS) via Kafka idempotence
- Event listener registration with annotation-based consumers
- Integration testing with Testcontainers

## Features

### Message Publishing

| Mode | Description | Use Case |
|------|-------------|----------|
| **Sync** | Blocks until broker acknowledgment | Critical events requiring guaranteed delivery |
| **Async** | Fire-and-forget, returns immediately | High-throughput scenarios where some loss is acceptable |
| **EOS (Idempotence)** | Exactly-once delivery with retries | Financial transactions, order processing |

### Consumer Mechanism

- Annotation-based event listeners (`@EventBusListener`)
- Automatic JSON deserialization
- Thread-safe event collection for testing

### Testing

- **Integration Tests**: Spin up real Kafka via Testcontainers
- **EOS Tests**: Verify idempotence configuration and exactly-once delivery
- **Performance Tests**: Compare throughput between EOS on/off modes

## Quick Start

### Prerequisites

- Docker (for Testcontainers integration tests)
- Maven 3.8+
- JDK 17+

### Running the Application

1. Ensure Kafka is running at `localhost:9092` (or update `src/main/resources/application.yml`)

2. Build the project:
```bash
cd eventbus-demo
mvn clean package -DskipTests
```

3. Run the application:
```bash
java -jar target/eventbus-demo-1.0.0.jar
```

### Running Tests

```bash
mvn test
```

This will:
- Spin up a Kafka container via Testcontainers
- Run all integration tests (SyncProducerTest, AsyncProducerTest, EosOnTest, EosOffTest)
- Tear down the container after tests complete

## Kerberos Authentication

The project supports Kerberos/GSSAPI authentication for Kafka. This requires the [kafka-kerberos-dev](../kafka-kerberos-dev/) environment.

### Prerequisites

1. Start the Kerberos Kafka environment:
```bash
cd /root/.openclaw/workspace-ceo/shinyi-demo/kafka-kerberos-dev
docker-compose up -d
```

2. Verify the environment is running:
```bash
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

### Configuration (`src/test/resources/application-kerberos.yml`)

```yaml
eventbus:
  kafka:
    kerberos:
      bootstrap-servers: localhost:9093
      topic: demo-kerberos-topic
      group-id: demo-kerberos-group
      # Kerberos authentication settings
      security-protocol: SASL_PLAINTEXT
      sasl-mechanism: GSSAPI
      kerberos-service-name: kafka
      kerberos-principal: kafka/kafka.example.com@EXAMPLE.COM
      kerberos-keytab: /root/.openclaw/workspace-ceo/shinyi-demo/kafka-kerberos-dev/kafka.keytab
      kerberos-krb5-location: /root/.openclaw/workspace-ceo/shinyi-demo/kafka-kerberos-dev/krb5.conf
```

### Kerberos Configuration Properties

| Parameter | Default | Description |
|-----------|---------|-------------|
| `security-protocol` | `SASL_PLAINTEXT` | Security protocol (SASL_PLAINTEXT or SASL_SSL) |
| `sasl-mechanism` | `GSSAPI` | SASL mechanism (GSSAPI for Kerberos) |
| `kerberos-service-name` | `kafka` | Kerberos service name |
| `kerberos-principal` | `kafka/kafka.example.com@EXAMPLE.COM` | Kerberos principal |
| `kerberos-keytab` | - | Path to keytab file |
| `kerberos-krb5-location` | - | Path to krb5.conf |

### Running Kerberos Tests

```bash
# Start Kerberos environment
cd /root/.openclaw/workspace-ceo/shinyi-demo/kafka-kerberos-dev
docker-compose up -d

# Run Kerberos configuration tests (no Docker needed)
mvn test -Dtest=KerberosConfigTest

# Run Kerberos integration tests (requires docker-compose)
mvn test -Dtest=KerberosProducerTest
```

### Kerberos Test Cases

| Test | Description |
|------|-------------|
| `testKerberosConfiguration` | Verify SASL/GSSAPI properties are correctly set |
| `testKerberosSystemProperties` | Verify krb5.conf system property is configured |
| `testKerberosConsumerProperties` | Verify consumer properties contain Kerberos settings |
| `testKerberosProduceConsume` | Full integration test (requires running environment) |

### Security Protocol Comparison

| Protocol | Encryption | Authentication | Use Case |
|----------|------------|----------------|----------|
| PLAINTEXT | None | None | Development only |
| SASL_PLAINTEXT | None | GSSAPI/Kerberos | Internal networks |
| SASL_SSL | TLS | GSSAPI/Kerberos | Production (recommended) |

## Configuration

### Main Configuration (`src/main/resources/application.yml`)

```yaml
eventbus:
  kafka:
    bootstrap-servers: localhost:9092
    topic: demo-topic
    group-id: demo-group

    # Producer tuning
    batch-size: 16384
    linger-ms: 1
    buffer-memory: 33554432
    compression-type: snappy

    # Consumer tuning
    max-poll-records: 500

    # EOS settings
    enable-idempotence: false
    enable-manual-commit: false
    commit-batch-size: 100
```

### Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `bootstrap-servers` | `localhost:9092` | Kafka broker address |
| `topic` | `demo-topic` | Default topic for events |
| `group-id` | `demo-group` | Consumer group ID |
| `batch-size` | 16384 | Producer batch size (bytes) |
| `linger-ms` | 1 | Producer linger time (ms) |
| `buffer-memory` | 33554432 | Producer buffer size (bytes) |
| `compression-type` | snappy | Compression algorithm |
| `max-poll-records` | 500 | Max records per poll |
| `enable-idempotence` | false | Enable EOS/idempotent producer |
| `enable-manual-commit` | false | Enable manual consumer commit |
| `commit-batch-size` | 100 | Manual commit batch size |

### EOS Mode Comparison

| Setting | EOS OFF (Default) | EOS ON |
|---------|------------------|--------|
| `enable.idempotence` | false | true |
| `acks` | 1 | all |
| `retries` | 3 | Integer.MAX_VALUE |
| Throughput | Higher | Lower (due to guarantees) |
| Delivery | At-least-once | Exactly-once |

## Architecture

### Components

```
src/main/java/com/shinyi/eventbus/demo/
├── EventBusDemoApplication.java    # Spring Boot entry point
├── config/
│   └── KafkaConfig.java           # Configuration properties
├── model/
│   └── DemoEvent.java             # Event data model
├── producer/
│   └── SimpleEventProducer.java   # Event publisher
└── consumer/
    └── SimpleEventConsumer.java   # Event listener
```

### Event Model

```java
@Data
public class DemoEvent implements Serializable {
    private long sequence;      // Unique sequence number
    private String message;     // Event payload
    private long timestamp;     // Event creation time
}
```

### Producer

The `SimpleEventProducer` provides three publishing methods:

- `publishSync(DemoEvent)` - Synchronous publish with blocking acknowledgment
- `publishAsync(DemoEvent)` - Asynchronous publish returning `CompletableFuture`
- `publish(DemoEvent)` - Simple fire-and-forget publish

### Consumer

The `SimpleEventConsumer` uses the `@EventBusListener` annotation:

```java
@EventBusListener(topic = "demo-topic", deserializeType = EVENT)
public void onDemoEvent(DemoEvent event) {
    // Process event
}
```

## Testing

### Test Structure

```
src/test/java/com/shinyi/eventbus/demo/
├── integration/
│   ├── BaseIntegrationTest.java     # Testcontainers setup
│   ├── SyncProducerTest.java        # Sync publish/consume
│   ├── AsyncProducerTest.java       # Async publish test
│   ├── EosOnTest.java              # Exactly-once semantics test
│   ├── EosOffTest.java             # High-throughput mode test
│   ├── BaseKerberosIntegrationTest.java  # Kerberos test base
│   └── KerberosProducerTest.java    # Kerberos/SASL authentication
└── unit/
    ├── ProducerTest.java           # Producer unit tests
    ├── ConsumerTest.java           # Consumer unit tests
    └── KerberosConfigTest.java     # Kerberos config unit tests
```

### BaseIntegrationTest

Provides:
- Kafka container initialization via Testcontainers
- Shared `KafkaConnectConfig` factory
- `EventListenerRegistryManager` setup
- `KafkaConsumer` helper for verification

### Test Cases

| Test | Description |
|------|-------------|
| `SyncProducerTest` | Verify sync publish delivers all messages |
| `AsyncProducerTest` | Verify async publish with fire-and-forget semantics |
| `EosOnTest` | Verify idempotence config and exactly-once delivery |
| `EosOffTest` | Verify high-throughput mode with 100 messages |

### Running Specific Tests

```bash
# Run only EOS tests
mvn test -Dtest=EosOnTest,EosOffTest

# Run only sync/async tests
mvn test -Dtest=SyncProducerTest,AsyncProducerTest
```

## Dependencies

- **Spring Boot** - Application framework
- **shinyi-eventbus** (1.1.1) - Event bus framework
- **Apache Kafka Clients** (3.6.1) - Kafka client library
- **Testcontainers** (1.19.3) - Docker-based integration testing
- **JUnit Jupiter** - Testing framework
- **Lombok** - Boilerplate reduction
- **Hutool** (5.8.12) - Utility library (required by eventbus)
- **Jackson** (2.15.2) - JSON serialization

## License

MIT License
