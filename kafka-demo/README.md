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

| Property | Description | Default |
|----------|-------------|---------|
| `kafka.demo.producer-message-count` | Number of messages to send | `10000` |
| `kafka.demo.producer-message-size` | Size of each message (bytes) | `1024` |
| `kafka.demo.producer-acks` | Acknowledgment level (`0`, `1`, `all`) | `1` |
| `kafka.demo.producer-batch-size` | Batch size | `16384` |
| `kafka.demo.producer-linger-ms` | Linger time | `1` |

### Consumer Settings

| Property | Description | Default |
|----------|-------------|---------|
| `kafka.demo.consumer-max-poll-records` | Max records per poll | `500` |
| `kafka.demo.consumer-auto-offset-reset` | Offset reset strategy | `earliest` |
| `kafka.demo.consumer-enable-auto-commit` | Auto commit enabled | `true` |

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

## Performance Testing

### Producer Test (5000 messages, 1KB each)
```
Total Messages: 5000
Successful: 5000, Failed: 0
Total Data: 4.88 MB
Duration: 1631 ms
Throughput: 5000.00 msg/sec
Bandwidth: 4.88 MB/sec
Avg Latency: 0.33 ms
```

### Consumer Test (5000 messages)
```
Total Messages: 5000
Successful: 5000, Failed: 0
Total Data: 4.88 MB
Duration: 3335 ms
Throughput: 1666.67 msg/sec
Bandwidth: 1.63 MB/sec
Avg Latency: 0.67 ms
```

### Running Performance Tests

```bash
# Package the application
mvn clean package spring-boot:repackage -DskipTests

# Run producer test
java -jar target/kafka-demo-1.0.0.jar \
  --kafka.test.mode=producer \
  --kafka.demo.bootstrap-servers=localhost:9092 \
  --kafka.demo.topic=test-topic \
  --kafka.demo.producer-message-count=5000

# Run consumer test
java -jar target/kafka-demo-1.0.0.jar \
  --kafka.test.mode=consumer \
  --kafka.demo.bootstrap-servers=localhost:9092 \
  --kafka.demo.topic=test-topic \
  --kafka.demo.group-id=test-group

# Run both (producer then consumer)
java -jar target/kafka-demo-1.0.0.jar \
  --kafka.test.mode=both \
  --kafka.demo.bootstrap-servers=localhost:9092 \
  --kafka.demo.topic=test-topic \
  --kafka.demo.producer-message-count=5000
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
