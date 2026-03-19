# Kafka Demo

Kafka producer and consumer performance testing with Shinyi EventBus framework.

## Configuration

### Kafka Connection

| Property | Description | Default |
|----------|-------------|---------|
| `kafka.demo.bootstrap-servers` | Kafka broker address | `10.10.10.30:9092` |
| `kafka.demo.topic` | Topic to use for testing | `test-topic` |
| `kafka.demo.group-id` | Consumer group ID | `test-group` |

### Producer Settings

| Property | Description | Default | Optimized |
|----------|-------------|---------|-----------|
| `kafka.demo.producer-message-count` | Number of messages to send | `10000` | - |
| `kafka.demo.producer-message-size` | Size of each message (bytes) | `1024` | - |
| `kafka.demo.producer-acks` | Acknowledgment level (`0`, `1`, `all`) | `1` | `1` |
| `kafka.demo.producer-batch-size` | Batch size | `16384` | `65536` |
| `kafka.demo.producer-linger-ms` | Linger time | `1` | `10` |
| `kafka.demo.producer-buffer-memory` | Buffer memory | `33554432` | `67108864` |
| `kafka.demo.producer-compression-type` | Compression type | `none` | `snappy` |
| `kafka.demo.producer-retries` | Number of retries | `3` | `3` |

### Consumer Settings

| Property | Description | Default | Optimized |
|----------|-------------|---------|-----------|
| `kafka.demo.consumer-max-poll-records` | Max records per poll | `500` | `5000` |
| `kafka.demo.consumer-auto-offset-reset` | Offset reset strategy | `earliest` | `earliest` |
| `kafka.demo.consumer-enable-auto-commit` | Auto commit enabled | `true` | `true` |
| `kafka.demo.consumer-fetch-min-bytes` | Min bytes per fetch | `1` | `1024` |
| `kafka.demo.consumer-fetch-max-wait-ms` | Max wait per fetch | `500` | `1000` |
| `kafka.demo.consumer-max-partition-fetch-bytes` | Max bytes per partition | `1048576` | `10485760` |

### Security Settings

| Property | Description | Values |
|----------|-------------|--------|
| `kafka.demo.security-protocol` | Security protocol | `PLAINTEXT`, `SASL_PLAINTEXT`, `SASL_SSL` |
| `kafka.demo.sasl-mechanism` | SASL mechanism | `PLAIN`, `SCRAM-SHA-256`, `SCRAM-SHA-512`, `GSSAPI` |
| `kafka.demo.username` | SASL username | - |
| `kafka.demo.password` | SASL password | - |

### Kerberos Settings

| Property | Description |
|----------|-------------|
| `kafka.demo.kerberos-service-name` | Kerberos service name |
| `kafka.demo.kerberos-principal` | Kerberos principal |
| `kafka.demo.kerberos-keytab` | Path to keytab file |
| `kafka.demo.kerberos-krb5-location` | Path to krb5.conf |

## Authentication Examples

### PLAINTEXT (No Authentication)
```yaml
kafka:
  demo:
    bootstrap-servers: 10.10.10.30:9092
    security-protocol: PLAINTEXT
```

### SASL/PLAIN
```yaml
kafka:
  demo:
    bootstrap-servers: 10.10.10.30:9092
    security-protocol: SASL_PLAINTEXT
    sasl-mechanism: PLAIN
    username: admin
    password: admin-secret
```

### SASL/SCRAM
```yaml
kafka:
  demo:
    bootstrap-servers: 10.10.10.30:9092
    security-protocol: SASL_SSL
    sasl-mechanism: SCRAM-SHA-512
    username: admin
    password: admin-secret
```

### Kerberos (GSSAPI)
```yaml
kafka:
  demo:
    bootstrap-servers: 10.10.10.30:9092
    security-protocol: SASL_PLAINTEXT
    sasl-mechanism: GSSAPI
    kerberos-service-name: kafka
    kerberos-principal: kafka/kafka.example.com@EXAMPLE.COM
    kerberos-keytab: /etc/kafka/kafka.keytab
    kerberos-krb5-location: /etc/kafka/krb5.conf
```

## Docker Compose Services

### Kafka PLAINTEXT
- Zookeeper: `localhost:2181`
- Kafka: `localhost:9092` (external), `kafka:9093` (internal)
- Kafka UI: `localhost:8081`

### Kafka SASL
- Zookeeper: `localhost:2181`
- Kafka: `localhost:9092` (SASL external), `kafka:9093` (PLAINTEXT internal)
- Kafka UI: `localhost:8081`
- Default credentials: `admin:admin-secret`

## Performance Benchmark Results

### Test Environment
- **Kafka**: 3.6.1 (Docker Confluent 7.5.0)
- **Message Size**: 1KB
- **Message Count**: 100,000

### Baseline vs Optimized

| Metric | Baseline | Optimized | Improvement |
|--------|----------|-----------|-------------|
| **Producer Throughput** | 12,500 msg/s | 33,333 msg/s | **2.67x** |
| **Producer Bandwidth** | 12.21 MB/s | 32.55 MB/s | **2.67x** |
| **Producer Latency** | 0.09 ms | 0.03 ms | **3x** |
| **Consumer Throughput** | 1,434 msg/s | 25,000 msg/s | **17.4x** |
| **Consumer Bandwidth** | 1.40 MB/s | 24.41 MB/s | **17.4x** |
| **Consumer Latency** | 0.71 ms | 0.04 ms | **17.75x** |

### 100K End-to-End Test

```
Producer Results:
  Total Messages: 100,000
  Duration: 3.06 seconds
  Throughput: 33,333 msg/sec
  Bandwidth: 32.55 MB/sec

Consumer Results:
  Total Messages: 100,000
  Duration: 4.31 seconds
  Throughput: 25,000 msg/sec
  Bandwidth: 24.41 MB/sec

Total End-to-End: ~7.4 seconds
```

### Optimized Configuration

#### Producer Optimized Settings
```properties
acks=1
batch.size=65536
linger.ms=10
buffer.memory=67108864
compression.type=snappy
retries=3
```

#### Consumer Optimized Settings
```properties
max.poll.records=5000
fetch.min.bytes=1024
fetch.max.wait.ms=1000
max.partition.fetch.bytes=10485760
```

## Advanced Optimizations

### 1. Compression (Producer)
```properties
compression.type=snappy  # or lz4 for better ratio
```
- Reduces network transfer by 20-30%
- Trade-off: CPU usage increases ~10-15%

### 2. Multi-Threading with Exactly-Once Semantics

For higher throughput with exactly-once guarantees, use transactional producer:

```java
// Enable idempotence for exactly-once producer
properties.put("enable.idempotence", true);
properties.put("max.in.flight.requests.per.connection", 5);
properties.put("acks", "all");
```

Consumer exactly-once with manual offset commit:
```java
// Disable auto commit
properties.put("enable.auto.commit", false);

// Process messages and commit manually after successful processing
consumer.commitSync();
```

### 3. Partition Strategy

For higher throughput, increase topic partitions:
```bash
kafka-topics.sh --alter --topic test-topic --partitions 12 --bootstrap-server localhost:9092
```

Then run multiple consumer instances equal to partition count.

### 4. Additional Broker Tuning

For the Docker Compose Kafka broker, add to `kafka-kerberos.yml` or `kafka-plaintext.yml`:

```yaml
environment:
  # Network threads
  KAFKA_NUM_NETWORK_THREADS: 8
  # IO threads
  KAFKA_NUM_IO_THREADS: 16
  # Socket buffer
  KAFKA_SOCKET_SEND_BUFFER_BYTES: 1024000
  KAFKA_SOCKET_RECEIVE_BUFFER_BYTES: 1024000
  # Max connections
  KAFKA_MAX_CONNECTIONS: 1000
```

## Performance Testing

### Quick Test (5K messages)
```bash
java -jar target/kafka-demo-1.0.0.jar \
  --kafka.test.mode=both \
  --kafka.demo.bootstrap-servers=localhost:9092
```

### 100K Benchmark (Optimized)
```bash
java -jar target/kafka-demo-1.0.0.jar \
  --kafka.test.mode=both \
  --kafka.demo.bootstrap-servers=localhost:9092 \
  --kafka.demo.topic=benchmark-optimized \
  --kafka.demo.producer-message-count=100000 \
  --kafka.demo.producer-message-size=1024 \
  --kafka.demo.producer-acks=1 \
  --kafka.demo.producer-batch-size=65536 \
  --kafka.demo.producer-linger-ms=10 \
  --kafka.demo.consumer-max-poll-records=5000
```

### Custom Performance Test
```bash
# Package
mvn clean package spring-boot:repackage -DskipTests

# Producer test
java -jar target/kafka-demo-1.0.0.jar \
  --kafka.test.mode=producer \
  --kafka.demo.bootstrap-servers=localhost:9092 \
  --kafka.demo.topic=test-topic \
  --kafka.demo.producer-message-count=100000

# Consumer test
java -jar target/kafka-demo-1.0.0.jar \
  --kafka.test.mode=consumer \
  --kafka.demo.bootstrap-servers=localhost:9092 \
  --kafka.demo.topic=test-topic \
  --kafka.demo.group-id=test-group

# Both (producer then consumer)
java -jar target/kafka-demo-1.0.0.jar \
  --kafka.test.mode=both \
  --kafka.demo.bootstrap-servers=localhost:9092 \
  --kafka.demo.topic=test-topic \
  --kafka.demo.producer-message-count=100000

# Kerberos test
java -jar target/kafka-demo-1.0.0.jar \
  --kafka.test.mode=kerberos-test
```

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=KafkaConnectConfigTest

# Run with Testcontainers (requires Docker)
mvn verify
```

## Shinyi EventBus Features

This demo uses the Shinyi EventBus library which provides:

- **@EventBusListener** - Annotation-based event listener registration
- **@EnableEventBus** - Enable EventBus in Spring Boot
- **EventListenerRegistryManager** - Centralized event publishing
- **Multi-MQ Support** - Kafka, RabbitMQ, RocketMQ, Redis support
- **Automatic Serialization** - JSON, Java serialization support
- **ThreadLocal Propagation** - TransmittableThreadLocal for context propagation

## License

Apache License 2.0 - See [shinyi-eventbus](https://github.com/MISAKIGA/shinyi-eventbus)
